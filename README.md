# sz-sso-client-starter

基于 [Sa-Token SSO 模式三](https://sa-token.cc/doc.html#/sso/sso-type3) 封装的 Spring Boot Starter，让业务服务只需实现少量 SPI 接口，即可快速接入 SSO 单点登录，并获得**首次登录默认角色初始化**与**平台超管状态同步**能力。

---

## 特性

- **零侵入**：Starter 自动装配所有 SSO 端点，业务代码无需关心 Sa-Token SSO 内部细节
- **类型安全**：泛型 SPI 设计，`buildLoginUser` / `createSession` 的用户对象类型 `<U>` 全程一致
- **可覆盖**：所有内置 Bean 均使用 `@ConditionalOnMissingBean`，业务方可按需覆盖
- **框架无关**：响应 DTO（`SsoApiResult`、`SsoLoginResult`）不依赖任何业务框架
- **首次登录默认角色**：新用户首次 SSO 登录时，自动写入默认角色到本地 DB（仅首次，后续登录不干预）
- **平台超管同步**：每次 SSO 登录从 Server 查询超管状态并同步到本地 DB，确保与平台一致
- **超管变更回传**：Client 内部变更超管角色后，通过 `SsoSyncHelper` 异步通知 Server，形成双向同步闭环
- **Spring Boot 3**：基于 `spring-boot-starter-parent 3.5.5`，使用 `AutoConfiguration.imports` 自动配置

---

## 快速开始

### 第一步：引入依赖

```xml
<dependency>
    <groupId>com.sz</groupId>
    <artifactId>sz-sso-client-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

> Starter 已内置 `sa-token-sso`、`sa-token-redis-template`、`sa-token-forest`、`commons-pool2`、`spring-boot-starter-web`，无需重复引入。

---

### 第二步：配置 application.yml

```yaml
sa-token:
  token-name: Authorization
  timeout: 604800          # token 固定超时（秒），7 天
  active-timeout: 86400    # 最低活跃时间（秒），1 天
  is-concurrent: true
  is-share: false
  token-style: uuid
  is-read-header: true
  is-read-cookie: false
  token-prefix: "Bearer"

  sso-client:
    mode: sso-client3                              # 固定使用模式三（HTTP 校验 ticket）
    client: your-app-name                          # 当前客户端标识，需在 SSO Server 端注册
    server-url: http://sso-server-host/api/admin   # SSO Server 根地址
    auth-url: http://sso-server-host/login         # 认证中心前端登录页地址
    is-http: true                                  # 模式三：开启 HTTP 校验
    is-slo: true                                   # 开启单点注销
    reg-logout-url: true                           # 登录时注册注销回调地址
    secret-key: your-secret-key                    # 与 SSO Server 共享的签名密钥
    push-url: http://your-app-host/sso/pushC       # 本客户端接收 Server 消息推送的地址
```

---

### 第三步：实现 SPI 接口

Starter 提供 5 个 SPI 接口，其中 2 个必须实现，3 个可选：

#### 3.1 `SsoUserMappingService` — 用户 ID 映射（**必须实现**）

负责 SSO Server 的 `centerId`（SSO 用户 ID）与本地 `loginId` 之间的双向映射，以及用户注册同步。

```java
@Component
@RequiredArgsConstructor
public class SsoUserMappingServiceImpl implements SsoUserMappingService {

    private final SysUserService userService;

    /** 本地 userId → SSO centerId（用于单点注销时转换 ID） */
    @Override
    public Object toServerUserId(Object clientUserId) {
        return userService.getSsoCenterId(Long.valueOf(clientUserId.toString()));
    }

    /** SSO centerId → 本地 userId（ticket 验证通过后调用，用户不存在则先创建） */
    @Override
    public Object toClientUserId(Object serverUserId) {
        return userService.findOrCreateByCenterId(Long.valueOf(serverUserId.toString()));
    }

    /** 处理 SSO Server 推送的用户注册消息，完成本地用户数据初始化 */
    @Override
    public void syncSsoRegisterUser(SaSsoMessage message) {
        SsoUserMeta meta = SsoUserMetaUtils.fromEntries(message.getDataMap().entrySet());
        userService.syncFromSso(meta);
    }
}
```

---

#### 3.2 `SsoLoginHandler<U>` — 构建用户对象（**必须实现**）

ticket 验证成功后，Starter 用本地 `userId` 调用此方法获取完整的用户对象（含权限、角色等）。

```java
@Component
@RequiredArgsConstructor
public class SsoLoginHandlerImpl implements SsoLoginHandler<LoginUser> {

    private final SysUserService userService;

    @Override
    public LoginUser buildLoginUser(Long userId) {
        return userService.buildLoginUser(userId);
    }
}
```

> `LoginUser` 是你的业务用户类型，需与 `SsoSessionCreator<U>` 的泛型参数保持一致。

---

#### 3.3 `SsoSessionCreator<U>` — 建立本地会话（**可选**）

Starter 内置 `DefaultSsoSessionCreator`（调用 `StpUtil.login` 后直接返回 token）。  
如需在 TokenSession 中额外存储用户信息，可覆盖此接口：

```java
@Component
public class SsoSessionCreatorImpl implements SsoSessionCreator<LoginUser> {

    @Override
    public SsoLoginResult createSession(LoginUser loginUser, SaLoginParameter parameter, Object loginId) {
        // 使用业务框架的登录工具，将 loginUser 写入 TokenSession
        LoginUtils.performLogin(loginUser, parameter, null);

        String accessToken = StpUtil.getTokenValue();
        long expireIn = StpUtil.getTokenTimeout();
        return SsoLoginResult.of(accessToken, expireIn, loginUser);
    }
}
```

> **不实现此接口**：`DefaultSsoSessionCreator` 自动生效，只执行 `StpUtil.login`，TokenSession 中不会写入用户对象，`LoginUtils.getLoginUser()` 将返回 `null`。

---

#### 3.4 `SsoClientRoleProvider` — 提供默认角色 key（**可选**）

告知 Starter 新用户首次 SSO 登录时应赋予的默认角色 key。此 key 将传递给 `SsoRoleBindingService.applyDefaultRole()` 使用。

```java
@Component
public class SsoClientRoleProviderImpl implements SsoClientRoleProvider {

    /**
     * 返回默认角色 key.
     * 具体含义由业务方定义（角色编码、permissions 字段等），
     * 与 SsoRoleBindingService 中的查询逻辑对应即可。
     */
    @Override
    public String getDefaultRoleKey() {
        return "dict_menu";
    }
}
```

> **不实现此接口**：首次登录的默认角色初始化步骤将跳过（`applyDefaultRole` 不会被调用）。超管状态同步不受影响，每次登录仍会正常执行 `applySuperAdmin`。

---

#### 3.5 `SsoRoleBindingService` — 执行角色绑定（**可选**）

处理两个职责：**首次登录默认角色初始化** 和 **平台超管状态本地同步**。

两个方法均在 `buildLoginUser()` **之前**调用，实现方只需操作 DB，后续 `buildLoginUser()` 从 DB 读取最新数据即可。

> 以下示例以 sz 框架为例，其他 Client 可结合自身业务框架自行实现。

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class SsoRoleBindingServiceImpl implements SsoRoleBindingService {

    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysUserService sysUserService;

    /**
     * 首次登录默认角色初始化.
     * 检查用户在本地是否已有任意角色，若无则按 defaultRoleKey 查找角色并写入 sys_user_role。
     */
    @Override
    public void applyDefaultRole(Long localUserId, String defaultRoleKey) {
        if (defaultRoleKey == null || defaultRoleKey.isBlank()) {
            return;
        }

        // 若用户已有任意角色（非首次登录），跳过
        long existingRoleCount = QueryChain.of(SysUserRole.class)
                .eq(SysUserRole::getUserId, localUserId)
                .count();
        if (existingRoleCount > 0) {
            return;
        }

        // 按 sys_role.permissions 查找默认角色
        SysRole role = sysRoleMapper.selectOneByQuery(
                QueryWrapper.create().eq(SysRole::getPermissions, defaultRoleKey)
        );
        if (role == null) {
            log.warn("applyDefaultRole: 未找到 permissions={} 的角色", defaultRoleKey);
            return;
        }

        // 写入 sys_user_role 表
        sysUserRoleMapper.insertBatchSysUserRole(List.of(role.getId()), localUserId);
        log.info("applyDefaultRole: 首次登录角色写入成功. userId={}, roleId={}", localUserId, role.getId());
    }

    /**
     * 根据平台超管状态同步本地超管角色/标记.
     * 每次 SSO 登录都会调用（不仅首次），确保与平台一致。
     *
     * sz 框架中复用 SysUserService.changeUserTag() 同时更新：
     *   - sys_user.user_tag_cd（1001002=超管 / 1001003=普通用户）
     *   - sys_user_role 中的超管角色绑定
     */
    @Override
    public void applySuperAdmin(Long localUserId, boolean isSuperAdmin) {
        SysUserTagDTO dto = new SysUserTagDTO();
        dto.setUserIds(Collections.singletonList(localUserId));
        dto.setUserTagCd(isSuperAdmin ? "1001002" : "1001003");
        sysUserService.changeUserTag(dto);
        log.info("applySuperAdmin: userId={}, isSuperAdmin={}", localUserId, isSuperAdmin);
    }
}
```

> **不实现此接口**：Starter 使用内置的 `DefaultSsoRoleBindingService`，仅打印 warn 日志提示，不做任何数据库写入。业务方可在 `buildLoginUser()` 中自行处理角色初始化逻辑。

---

### 第四步：超管变更同步（Client → Server）

当 Client 内部变更了用户的超管身份（赋予或撤销）后，需要调用 `SsoSyncHelper.syncSuperAdmin()` 将变更异步通知 Server，使 Server 的超管名单保持最新。

```java
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    // SsoSyncHelper 由 Starter 自动注册；非 SSO 环境下不存在此 Bean
    @Autowired(required = false)
    private SsoSyncHelper ssoSyncHelper;

    public void changeUserTag(SysUserTagDTO dto) {
        String userTagCd = dto.getUserTagCd();
        List<Long> userIds = dto.getUserIds();

        // ... 本地 DB 操作：更新 user_tag_cd、增删超管角色 ...

        // 通知 Server 同步超管状态（异步，失败不影响本地操作）
        if (ssoSyncHelper != null) {
            boolean isSuperAdmin = "1001002".equals(userTagCd);
            for (Long userId : userIds) {
                ssoSyncHelper.syncSuperAdmin(userId, isSuperAdmin);
            }
        }
    }
}
```

> `SsoSyncHelper.syncSuperAdmin()` 标注了 `@Async`，内部会通过 `SsoUserMappingService.toServerUserId()` 将本地 userId 转为 centerId，再发送 `SYNC_SUPER_ADMIN` 消息给 Server。失败时仅打印 warn 日志，不抛出异常。

---

### 第五步：验证自动装配

启动应用后，日志中出现以下输出即说明接入成功：

```
SSO Client 自动配置完成: 策略函数和消息处理器已注册
SSO Client 自动配置: 注册 SsoClientService
```

---

## 自动注册的端点

Starter 自动注册以下 REST 端点（路径前缀 `/sso`），**无需任何额外配置**，也不会被 Sa-Token 拦截器拦截（已标注 `@SaIgnore`）：

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/sso/isLogin` | 查询当前登录状态，返回 `hasLogin`、`loginId` |
| `GET` | `/sso/getSsoAuthUrl` | 根据 `clientLoginUrl` 参数生成 SSO 认证中心跳转地址 |
| `GET` | `/sso/doLoginByTicket` | 凭 `ticket` 完成本地登录，返回 `accessToken` 及用户信息 |
| `ANY` | `/sso/logout` | 发起单点注销（重定向到 SSO Server 执行全局下线） |
| `ANY` | `/sso/logoutCall` | 接收 SSO Server 下发的单点注销回调 |
| `ANY` | `/sso/pushC` | 接收 SSO Server 推送的消息（如用户注册同步、超管查询等） |

---

## SPI 接口一览

| 接口 | 泛型 | 是否必须 | 内置默认实现 | 不实现的后果 |
|------|------|---------|-------------|------------|
| `SsoUserMappingService` | 无 | **必须** | 无 | 自动装配不激活 |
| `SsoLoginHandler<U>` | `U` = 用户对象类型 | **必须** | 无 | 自动装配不激活 |
| `SsoSessionCreator<U>` | `U` = 用户对象类型 | 可选 | `DefaultSsoSessionCreator` | 只执行 `StpUtil.login`，TokenSession 不写入用户对象 |
| `SsoClientRoleProvider` | 无 | 可选 | 无 | 首次登录默认角色初始化跳过 |
| `SsoRoleBindingService` | 无 | 可选 | `DefaultSsoRoleBindingService`（仅 warn 日志） | 默认角色不写入本地 DB，超管状态不同步到本地 DB |

> `SsoLoginHandler<U>` 与 `SsoSessionCreator<U>` 的泛型参数 `U` 必须是**同一类型**。

---

## 响应格式

业务端点统一使用 `SsoApiResult<T>` 包装：

```json
{
  "code": "0000",
  "message": "SUCCESS",
  "data": { ... },
  "param": {}
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | `String` | 响应码，成功为 `"0000"` |
| `message` | `String` | 响应信息，成功为 `"SUCCESS"` |
| `data` | `T` | 业务数据 |
| `param` | `Object` | 扩展参数（默认为空对象） |

---

## 工具类

### `SsoClientUtil`

在业务代码中判断当前登录用户是否为平台超管：

```java
// 在任意已登录的请求中判断超管身份
boolean isSuperAdmin = SsoClientUtil.isSuperAdmin();
```

> 该值在每次 SSO 登录时由 `QUERY_USER_ROLES` 消息从 Server 取回并写入 TokenSession。  
> 若当前用户未登录、或 SSO 查询失败（Server 不可达时降级），则返回 `false`。

---

### `SsoSyncHelper`

Client 内部超管角色变更（赋予/撤销）后，调用此 Bean 将变更异步同步到 Server：

```java
@Autowired(required = false)
private SsoSyncHelper ssoSyncHelper;

// 赋予超管后通知 Server
ssoSyncHelper.syncSuperAdmin(localUserId, true);

// 撤销超管后通知 Server
ssoSyncHelper.syncSuperAdmin(localUserId, false);
```

> `syncSuperAdmin()` 标注 `@Async`，内部将 localUserId 转为 centerId 后发送 `SYNC_SUPER_ADMIN` 消息。  
> 失败时仅打印 warn 日志，不影响 Client 本地操作。  
> 非 SSO 环境下此 Bean 不存在，注入时请使用 `@Autowired(required = false)` 或 `Optional<SsoSyncHelper>`。

---

### `SsoUserMetaUtils`

用于 SSO Server 推送消息中用户元信息的序列化/反序列化：

```java
// SsoUserMeta → Map（用于消息序列化）
Map<String, Object> map = SsoUserMetaUtils.toMap(meta);

// Map → SsoUserMeta
SsoUserMeta meta = SsoUserMetaUtils.fromMap(map);

// 直接从消息 DataMap 的 Entry 集合解析（推荐在 syncSsoRegisterUser 中使用）
SsoUserMeta meta = SsoUserMetaUtils.fromEntries(message.getDataMap().entrySet());
```

`SsoUserMeta` 字段说明：

| 字段 | 类型 | 说明 |
|------|------|------|
| `ssoUserId` | `Long` | SSO Server 用户 ID |
| `username` | `String` | 用户名 |
| `nickname` | `String` | 昵称 |
| `email` | `String` | 邮箱 |
| `phone` | `String` | 手机号 |
| `avatarUrl` | `String` | 头像地址 |
| `createTime` | `LocalDateTime` | 注册时间 |

---

## 消息常量

```java
SsoCoreConstant.MESSAGE_REGISTER           // "REGISTER"           — 用户注册同步消息
SsoCoreConstant.MESSAGE_USER_CHECK         // "USER_CHECK"         — 用户信息查询消息（降级）
SsoCoreConstant.MESSAGE_QUERY_USER_ROLES   // "QUERY_USER_ROLES"   — 登录时查询超管状态（Server→Client）
SsoCoreConstant.MESSAGE_SYNC_SUPER_ADMIN   // "SYNC_SUPER_ADMIN"   — 超管变更同步（Client→Server）
SsoCoreConstant.SESSION_KEY_IS_SUPER_ADMIN // "isSuperAdmin"       — TokenSession 中存储超管状态的 key（Boolean）
```

---

## 自动装配条件

Starter 的自动配置只在满足以下所有条件时才会激活，**否则对应用零影响**：

| 条件 | 说明 |
|------|------|
| `@ConditionalOnClass(SaSsoClientTemplate.class)` | classpath 中存在 `sa-token-sso` |
| `@ConditionalOnBean(SsoUserMappingService.class)` | 业务方已提供 `SsoUserMappingService` Bean |
| `@ConditionalOnBean(SsoLoginHandler.class)` | 业务方已提供 `SsoLoginHandler` Bean |

---

## 最小化接入示例

不依赖数据库、仅用内存 Map 模拟的最小化接入（适合快速验证联通性）：

```java
// 1. 用户 ID 映射（内存模拟）
@Component
public class MinimalMappingService implements SsoUserMappingService {

    private final Map<Object, Long> centerToLocal = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(1);

    @Override
    public Object toServerUserId(Object clientUserId) {
        return centerToLocal.entrySet().stream()
                .filter(e -> e.getValue().equals(clientUserId))
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }

    @Override
    public Object toClientUserId(Object serverUserId) {
        return centerToLocal.computeIfAbsent(serverUserId, k -> idGen.getAndIncrement());
    }

    @Override
    public void syncSsoRegisterUser(SaSsoMessage message) {
        SsoUserMeta meta = SsoUserMetaUtils.fromEntries(message.getDataMap().entrySet());
        System.out.println("收到注册同步: " + meta.getUsername());
    }
}

// 2. 构建用户对象（直接返回 userId，生产环境替换为数据库查询）
@Component
public class MinimalLoginHandler implements SsoLoginHandler<Long> {

    @Override
    public Long buildLoginUser(Long userId) {
        return userId;
    }
}

// 3. SsoSessionCreator 无需实现，DefaultSsoSessionCreator 自动生效
// 4. SsoClientRoleProvider 无需实现，首次登录默认角色初始化跳过
// 5. SsoRoleBindingService 无需实现，DefaultSsoRoleBindingService 自动生效（仅 warn 日志）
//    → 超管状态仍会从 Server 查询并存入 TokenSession
//    → SsoClientUtil.isSuperAdmin() 可正常使用
//    → 但不会写入本地 DB（无 applySuperAdmin 实际实现）
```

---

## 本地调试（IDEA Import Module）

1. 在 IDEA 中打开你的主项目
2. `File → Project Structure → Modules → + → Import Module`
3. 选择 `sz-sso-client-starter` 目录，以 Maven 模块方式导入
4. 在消费方 `pom.xml` 中确保依赖版本为 `1.0.0`
5. IDEA 会自动通过本地源码解析，修改 Starter 后无需 `mvn install`，增量编译即可生效

---

## 版本要求

| 依赖 | 版本 |
|------|------|
| Java | 21+ |
| Spring Boot | 3.x |
| Sa-Token | 1.44.0 |

---

---

## 设计背景与架构

> 本章节面向希望深入理解超管同步机制和登录流程设计思路的读者。快速接入无需阅读此章节。

---

### 整体架构

本 Starter 处于 SSO Server 与业务 Client 系统之间，承担协议编排的职责。三方关系如下：

```
┌──────────────────────────────────────────────────────┐
│                     SSO Server                       │
│                                                      │
│  职责：                                               │
│  · 统一管理用户身份（注册 / 登录 / 注销）              │
│  · 维护超管名单（sso_user_client_role 表）             │
│  · 回答「该用户是否为该 Client 的超管」                 │
│  · 接收 Client 超管变更通知并更新名单                   │
│                                                      │
│  数据（权威来源）：                                    │
│  sso_user_client_role { center_id, client_id }       │
│  有记录 = 超管，无记录 = 普通用户                      │
│                                                      │
│  消息处理：                                           │
│  QUERY_USER_ROLES  → 查表 → 返回 isSuperAdmin        │
│  SYNC_SUPER_ADMIN  → 写表 → 更新超管名单              │
└────────────┬─────────────────────────┬───────────────┘
             │                         ▲
             │  ① 登录时查询            │  ② 变更时同步
             │  QUERY_USER_ROLES       │  SYNC_SUPER_ADMIN
             │  → isSuperAdmin: bool   │  ← localUserId + bool
             ▼                         │
┌────────────▼─────────────────────────┴───────────────┐
│               sz-sso-client-starter                  │
│                                                      │
│  职责：                                               │
│  · 编排完整的 5 步登录流程                             │
│  · 向 Server 查询超管状态并存入 TokenSession           │
│  · 提供 SsoSyncHelper 将变更回传 Server               │
│  · 提供 SPI 扩展点，不做任何业务决策                   │
│                                                      │
│  TokenSession:                                       │
│  "loginUser"    → Client 本地权限对象（由业务方写入）   │
│  "isSuperAdmin" → Boolean（由 Starter 写入）          │
└────────────────────────┬─────────────────────────────┘
                         │
                         │  SPI 回调
                         │  · SsoUserMappingService（ID 映射）
                         │  · SsoLoginHandler（构建用户对象）
                         │  · SsoClientRoleProvider（提供默认角色 key）
                         │  · SsoRoleBindingService（执行 DB 操作）
                         │  · SsoSessionCreator（建立 Session）
                         │
┌────────────────────────▼─────────────────────────────┐
│                业务 Client 系统                       │
│                                                      │
│  职责：                                               │
│  · 实现 SPI 接口，对接本地用户体系                     │
│  · 拥有完整的本地权限体系（sys_role / sys_user_role）  │
│  · 内部超管变更后调用 SsoSyncHelper 通知 Server        │
│  · 细粒度权限控制完全由 Client 自己负责                 │
└──────────────────────────────────────────────────────┘
```

---

### 粗粒度与细粒度的权限分工

SSO Server 与业务 Client 之间存在清晰的权限职责边界：

```
SSO Server（粗粒度）                     业务 Client（细粒度）
┌────────────────────────┐              ┌────────────────────────────────┐
│                        │              │                                │
│  只回答一个问题：        │   登录同步   │  拥有完整的本地权限体系：        │
│  「这个人是不是超管？」   │ ──────────→ │  · 角色（sys_role）             │
│                        │              │  · 用户角色（sys_user_role）    │
│  维护一张极简名单：      │   变更回传   │  · 菜单权限（sys_menu）         │
│  sso_user_client_role  │ ←────────── │  · 数据范围（data_scope）       │
│  { center_id,          │              │  · ...                         │
│    client_id }         │              │                                │
│                        │              │  由 Client 内部有权限的角色      │
│                        │              │  （通常是超管）自主管理          │
└────────────────────────┘              └────────────────────────────────┘
```

**Server 只做粗粒度控制**：维护"谁是哪个 Client 的超管"这一张名单，核心目的是解决 Client 的**冷启动问题** — 一个全新接入的 Client 系统，本地没有任何用户数据，第一个超管从哪来？由平台管理员在 SSO 管理后台指定即可。

**Client 拥有细粒度控制**：角色定义、权限分配、数据范围划分等完全由 Client 自己负责。Client 内部有操作权限的角色（通常就是平台指定的那个超管）可以自主管理所有用户的权限，包括赋予或撤销其他用户的超管身份。Server 不感知也不干预 Client 内部的权限结构。

简单来说：**Server 负责"把超管送进门"，之后屋子里怎么管，全由 Client 自己说了算。**

---

### 5 步登录流程

Starter 的登录流程经过精心设计：**先写 DB 再构建用户对象**。这样 `buildLoginUser()` 从 DB 读取时，已经包含了默认角色和超管状态，无需手动操作内存中的用户对象。

```
ticket 校验通过，获得 localUserId
            │
            ▼
┌─── Step 1: applyDefaultRole(userId, "dict_menu") ────────────┐
│   · 查 sys_user_role：用户已有任意角色？→ 跳过（非首次登录）     │
│   · 无角色 → 按 defaultRoleKey 查 sys_role → 写入 sys_user_role │
│   · 仅当 SsoClientRoleProvider Bean 存在时执行                  │
└──────────────────────────────────────────────────────────────┘
            │
            ▼
┌─── Step 2: queryIsSuperAdmin(userId) ────────────────────────┐
│   · toServerUserId(userId) → centerId                         │
│   · 向 Server 发送 QUERY_USER_ROLES { centerId, clientId }   │
│   · Server 查 sso_user_client_role 表 → 返回 true/false      │
│   · 失败降级为 false（不中断登录）                              │
└──────────────────────────────────────────────────────────────┘
            │
            ▼
┌─── Step 3: applySuperAdmin(userId, isSuperAdmin) ────────────┐
│   · 每次登录都执行（不仅首次），确保与平台一致                   │
│   · 写入本地 DB（如 user_tag_cd、超管角色绑定等）               │
│   · 仅当 SsoRoleBindingService Bean 存在时执行                 │
└──────────────────────────────────────────────────────────────┘
            │
            ▼
┌─── Step 4: buildLoginUser(userId) ───────────────────────────┐
│   · 从本地 DB 构建完整用户对象                                  │
│   · 此时 DB 已包含 Step1 的默认角色 + Step3 的超管状态          │
│   · roles、permissions、depts、dataScope 等一次性读取           │
└──────────────────────────────────────────────────────────────┘
            │
            ▼
┌─── Step 5: createSession(user, parameter, loginId) ──────────┐
│   · 业务方的 SsoSessionCreator 建立 Session                    │
│   · Starter 额外写入 isSuperAdmin Boolean 到 TokenSession     │
│   · 返回 accessToken + expireIn + userInfo                    │
└──────────────────────────────────────────────────────────────┘
```

**为什么 applyDefaultRole 和 applySuperAdmin 在 buildLoginUser 之前？**

`buildLoginUser()` 通常从 DB 完整构建用户信息（角色、权限、部门、数据范围等），这些字段之间存在关联（如角色决定了权限列表）。如果在 `buildLoginUser()` 之后手动修改 `loginUser.getRoles()`，关联的 `permissions`、`dataScope` 等不会自动更新，导致数据不一致。

先写 DB 再构建用户对象，让 `buildLoginUser()` 自然读取到完整的最新数据，避免了内存对象操作的复杂性。

---

### 超管同步闭环

超管状态通过两条通道形成双向同步闭环：

```
┌─────────────┐          QUERY_USER_ROLES            ┌─────────────┐
│             │  ──────────────────────────────────→  │             │
│   Client    │          isSuperAdmin: bool           │   Server    │
│             │  ←──────────────────────────────────  │             │
│             │                                       │  (权威来源)  │
│             │          SYNC_SUPER_ADMIN             │             │
│             │  ──────────────────────────────────→  │             │
│             │          centerId + bool              │             │
└─────────────┘                                       └─────────────┘

通道 1（Server → Client）：登录时查询
  · 每次 SSO 登录触发
  · Starter 向 Server 发送 QUERY_USER_ROLES
  · Server 查 sso_user_client_role 表
  · 返回 isSuperAdmin → applySuperAdmin 写入本地 DB
  · isSuperAdmin 同时存入 TokenSession

通道 2（Client → Server）：变更时通知
  · Client 内部变更超管角色时触发
  · 业务代码调用 SsoSyncHelper.syncSuperAdmin()
  · 异步发送 SYNC_SUPER_ADMIN 消息
  · Server 更新 sso_user_client_role 表
```

**为什么 Server 是权威来源，Client 也能反向同步？**

平台管理员通过 SSO 管理后台设定各 Client 的初始超管名单，这是超管体系的**起点**。但接入后，Client 内部的超管可能在本地管理界面变更其他用户的超管身份（赋予或撤销），此时 Client 需要将变更通知 Server，保持双方数据一致。

通道 1 确保每次登录都能拿到最新的超管状态（即使 Client 本地数据被意外修改）；通道 2 确保 Client 主动变更后 Server 也能及时知晓。

---

### 降级与容错

| 场景 | 处理策略 | 影响 |
|------|---------|------|
| Server 不可达（登录时 `QUERY_USER_ROLES` 失败） | `isSuperAdmin` 降级为 `false` | 用户以非超管身份登录，不中断登录流程 |
| `applyDefaultRole` 异常 | catch + warn 日志，继续后续步骤 | 首次登录可能无默认角色，但登录不受影响 |
| `applySuperAdmin` 异常 | catch + warn 日志，继续后续步骤 | 本地超管状态可能未更新，但登录不受影响 |
| `SsoSyncHelper.syncSuperAdmin` 失败 | `@Async` + catch + warn 日志 | Client 本地变更已生效，Server 暂未同步 |

核心原则：**登录流程不因任何可选步骤的失败而中断**。超管状态查询和同步都是"尽最大努力"语义，失败时降级为安全默认值（非超管）。

---

### Server 端配套说明

> 具体实现见 SSO Server 项目文档，此处仅说明概念。

Server 端需要配套以下内容：

**数据库**

```sql
-- 超管名单，只记录「谁是哪个 Client 的超管」
CREATE TABLE sso_user_client_role (
    id          bigint      PRIMARY KEY AUTO_INCREMENT,
    center_id   bigint      NOT NULL COMMENT 'SSO Server 用户 ID',
    client_id   varchar(64) NOT NULL COMMENT '对应 sso_client.client_id',
    create_time datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  bigint      COMMENT '操作的平台管理员 ID',
    UNIQUE KEY uk_user_client (center_id, client_id)
);
```

**消息处理器（两个）**

| 消息类型 | 方向 | 处理逻辑 |
|---------|------|---------|
| `QUERY_USER_ROLES` | Client → Server | 接收 `{ centerId, clientId }`，查 `sso_user_client_role` 表，返回 `isSuperAdmin: true/false` |
| `SYNC_SUPER_ADMIN` | Client → Server | 接收 `{ centerId, clientId, isSuperAdmin }`，`true` 时 upsert 记录，`false` 时 delete 记录 |

**管理端**

平台管理员通过管理端 UI 维护各 Client 的超管用户名单（增加/撤销超管身份），这是整个超管体系的起点。
