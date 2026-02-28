# sz-sso-client-starter

## 背景

[sz-sso-server](https://github.com/xxx/sz-sso-server) 是一个基于 [Sa-Token](https://sa-token.cc) 搭建的 SSO 服务平台，提供**单点登录、单点注销、统一认证中心**等核心能力，同时支持 GitHub、微信、微博等**三方登录**接入（更多功能持续完善中）。

`sz-sso-client-starter` 是该平台配套的 Client 端 Spring Boot Starter，基于 [Sa-Token SSO 模式三](https://sa-token.cc/doc.html#/sso/sso-type3) 封装。业务系统只需引入此 Starter 并实现少量 SPI 接口，即可接入 SSO 单点登录，并获得平台级超管指定与默认角色初始化能力。

---

## 它解决什么问题

一个全新的业务系统接入 SSO 后，面临两个冷启动问题：

1. **第一个超管从哪来？** — 本地用户表是空的，没人有权限进后台管理
2. **新用户首次登录给什么权限？** — 没有角色就什么都看不到

本 Starter 通过与 SSO Server 协作解决这两个问题：平台管理员在 Server 端指定超管名单，Starter 在登录时自动查询并同步到 Client 本地；同时支持为首次登录的新用户自动写入默认角色。

---

## 核心概念

### 架构总览

```
┌──────────────────────────────────────────────────────────┐
│                      SSO Server                          │
│                                                          │
│  · 统一身份管理（注册 / 登录 / 注销）                       │
│  · 维护超管名单：sso_user_client_role { center_id, client_id } │
│  · 有记录 = 超管，无记录 = 普通用户                          │
│                                                          │
│  消息处理：                                                │
│  · QUERY_USER_ROLES → 查表 → 返回 isSuperAdmin            │
│  · SYNC_SUPER_ADMIN → 写表 → 更新超管名单                  │
└──────────────┬────────────────────────┬──────────────────┘
               │                        ▲
     ① 登录时查询                ② 变更时回传
     QUERY_USER_ROLES           SYNC_SUPER_ADMIN
     → isSuperAdmin: bool      ← userId + bool
               ▼                        │
┌──────────────▼────────────────────────┴──────────────────┐
│                sz-sso-client-starter                     │
│                                                          │
│  · 编排 5 步登录流程（详见下文）                             │
│  · 查询超管状态，存入 TokenSession                          │
│  · 提供 SsoSyncHelper，将本地变更回传 Server                │
│  · 通过 SPI 扩展点对接业务，自身不做业务决策                   │
└──────────────────────────┬───────────────────────────────┘
                           │  SPI 回调
┌──────────────────────────▼───────────────────────────────┐
│                   业务 Client 系统                        │
│                                                          │
│  · 实现 SPI 接口，对接本地用户 / 角色 / 权限体系             │
│  · 拥有完整的细粒度权限控制                                  │
│  · 内部超管变更后调用 SsoSyncHelper 通知 Server              │
└──────────────────────────────────────────────────────────┘
```

---

### 粗粒度与细粒度的权限分工

SSO Server 与业务 Client 有清晰的职责边界：

|              | SSO Server（粗粒度）                                    | 业务 Client（细粒度）                                    |
| ------------ | ------------------------------------------------------- | -------------------------------------------------------- |
| **管什么**   | 只管一件事：谁是哪个 Client 的超管                      | 角色、菜单权限、数据范围等完整权限体系                   |
| **数据**     | 一张表：`sso_user_client_role { center_id, client_id }` | `sys_role`、`sys_user_role`、`sys_menu`、`data_scope` 等 |
| **谁来操作** | 平台管理员（在 SSO 管理后台）                           | Client 内部有权限的角色（通常就是超管）                  |
| **核心目的** | 解决冷启动：把第一个超管送进门                          | 接管后续所有权限管理                                     |

**Server 不感知也不干预 Client 内部的权限结构。** 平台把超管指定好之后，Client 里怎么分配角色、怎么划分数据范围，全由 Client 自己说了算。Client 的超管甚至可以在本地赋予或撤销其他用户的超管身份——变更后通过 `SsoSyncHelper` 通知 Server 保持同步即可。

---

### 两大能力

#### 能力一：超管状态双向同步

```
平台管理员在 SSO 管理后台指定超管
              │
              ▼
┌─ 通道 1：登录时查询（Server → Client）──────────────────┐
│  每次 SSO 登录 → Starter 发送 QUERY_USER_ROLES          │
│  → Server 查表 → 返回 isSuperAdmin                      │
│  → applySuperAdmin() 写入本地 DB                        │
│  → isSuperAdmin 存入 TokenSession                       │
└─────────────────────────────────────────────────────────┘

Client 内部超管在本地管理界面变更某用户的超管身份
              │
              ▼
┌─ 通道 2：变更时回传（Client → Server）──────────────────┐
│  业务代码调用 SsoSyncHelper.syncSuperAdmin()             │
│  → 异步发送 SYNC_SUPER_ADMIN 消息                       │
│  → Server 更新 sso_user_client_role 表                  │
└─────────────────────────────────────────────────────────┘
```

通道 1 保证每次登录拿到最新状态；通道 2 保证 Client 主动变更后 Server 也能及时知晓。

#### 能力二：首次登录默认角色初始化

新用户通过 SSO 首次登录某个 Client 时，本地没有任何角色记录。Starter 会调用 `SsoRoleBindingService.applyDefaultRole()` 将一个默认角色写入本地 DB（如"字典菜单查看"角色），让用户至少能看到基础页面。

- 仅首次生效：用户已有任意角色时跳过
- 后续登录不干预：角色管理完全交由 Client 内部权限体系

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

> 已内置 `sa-token-sso`、`sa-token-redis-template`、`sa-token-forest`、`commons-pool2`、`spring-boot-starter-web`，无需重复引入。

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

Starter 通过 SPI 接口与业务系统对接。**2 个必须实现，3 个可选。**

#### 必须实现

##### `SsoUserMappingService` — 用户 ID 映射

SSO Server 用 `centerId` 标识用户，Client 用本地 `userId`。此接口负责双向转换和用户注册同步。

```java
@Component
@RequiredArgsConstructor
public class SsoUserMappingServiceImpl implements SsoUserMappingService {

    private final SysUserService userService;

    @Override
    public Object toServerUserId(Object clientUserId) {
        return userService.getSsoCenterId(Long.valueOf(clientUserId.toString()));
    }

    @Override
    public Object toClientUserId(Object serverUserId) {
        return userService.findOrCreateByCenterId(Long.valueOf(serverUserId.toString()));
    }

    @Override
    public void syncSsoRegisterUser(SaSsoMessage message) {
        SsoUserMeta meta = SsoUserMetaUtils.fromEntries(message.getDataMap().entrySet());
        userService.syncFromSso(meta);
    }
}
```

##### `SsoLoginHandler<U>` — 构建用户对象

从本地 DB 构建完整的用户对象（含角色、权限等），供后续 Session 创建使用。

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

> 泛型 `U` 是你的业务用户类型，需与 `SsoSessionCreator<U>` 保持一致。

---

#### 可选实现

##### `SsoSessionCreator<U>` — 建立本地会话

控制 Session 的建立方式。不实现时，内置 `DefaultSsoSessionCreator` 自动生效（仅执行 `StpUtil.login`，TokenSession 不写入用户对象）。

如需将用户对象存入 TokenSession：

```java
@Component
public class SsoSessionCreatorImpl implements SsoSessionCreator<LoginUser> {

    @Override
    public SsoLoginResult createSession(LoginUser loginUser, SaLoginParameter parameter, Object loginId) {
        LoginUtils.performLogin(loginUser, parameter, null);
        String accessToken = StpUtil.getTokenValue();
        long expireIn = StpUtil.getTokenTimeout();
        return SsoLoginResult.of(accessToken, expireIn, loginUser);
    }
}
```

##### `SsoClientRoleProvider` — 提供默认角色 key

告知 Starter 首次登录时应赋予的默认角色标识。此 key 会传递给 `SsoRoleBindingService.applyDefaultRole()` 使用。

```java
@Component
public class SsoClientRoleProviderImpl implements SsoClientRoleProvider {

    @Override
    public String getDefaultRoleKey() {
        return "dict_menu";
    }
}
```

> 不实现此接口时，首次登录的默认角色初始化步骤跳过。超管状态同步不受影响。

##### `SsoRoleBindingService` — 执行角色绑定

两个职责：**首次登录默认角色初始化** + **超管状态本地同步**。

两个方法均在 `buildLoginUser()` **之前**调用，只需操作 DB，`buildLoginUser()` 会自动读取最新数据。

> 以下示例以 sz 框架为例，其他 Client 可结合自身业务自行实现。

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
     * 用户已有任意角色时跳过；无角色时按 defaultRoleKey 写入。
     */
    @Override
    public void applyDefaultRole(Long localUserId, String defaultRoleKey) {
        if (defaultRoleKey == null || defaultRoleKey.isBlank()) {
            return;
        }
        long existingRoleCount = QueryChain.of(SysUserRole.class)
                .eq(SysUserRole::getUserId, localUserId)
                .count();
        if (existingRoleCount > 0) {
            return;
        }
        SysRole role = sysRoleMapper.selectOneByQuery(
                QueryWrapper.create().eq(SysRole::getPermissions, defaultRoleKey)
        );
        if (role == null) {
            log.warn("applyDefaultRole: 未找到 permissions={} 的角色", defaultRoleKey);
            return;
        }
        sysUserRoleMapper.insertBatchSysUserRole(List.of(role.getId()), localUserId);
        log.info("applyDefaultRole: 首次登录角色写入成功. userId={}, roleId={}", localUserId, role.getId());
    }

    /**
     * 同步超管状态到本地.
     * 每次登录都执行（不仅首次），确保与平台一致。
     * sz 框架中复用 changeUserTag() 同时更新 user_tag_cd 和超管角色绑定。
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

> 不实现此接口时，内置 `DefaultSsoRoleBindingService` 仅打印 warn 日志，不做 DB 操作。

---

### 第四步：验证自动装配

启动应用后，日志中出现以下输出即说明接入成功：

```
SSO Client 自动配置完成: 策略函数和消息处理器已注册
SSO Client 自动配置: 注册 SsoClientService
```

---

## 超管变更同步（Client → Server）

上面的 SPI 接口解决了**登录时** Server → Client 方向的同步。还有一个方向需要处理：**Client 内部变更超管后通知 Server。**

当 Client 内部有权限的角色（通常是超管自己）在本地管理界面赋予或撤销了某用户的超管身份时，需要调用 `SsoSyncHelper.syncSuperAdmin()` 将变更回传 Server，保持双方数据一致。

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

        // 通知 Server 同步（异步，失败不影响本地操作）
        if (ssoSyncHelper != null) {
            boolean isSuperAdmin = "1001002".equals(userTagCd);
            for (Long userId : userIds) {
                ssoSyncHelper.syncSuperAdmin(userId, isSuperAdmin);
            }
        }
    }
}
```

> `syncSuperAdmin()` 标注 `@Async`，内部通过 `SsoUserMappingService.toServerUserId()` 将本地 userId 转为 centerId，再发送 `SYNC_SUPER_ADMIN` 消息。失败时仅打印 warn 日志，不抛异常。

---

## 登录流程详解

Starter 的登录流程采用**先写 DB 再构建用户**的设计。这样 `buildLoginUser()` 从 DB 读取时，已经包含了前序步骤写入的默认角色和超管状态，无需手动操作内存对象。

```
ticket 校验通过，获得 localUserId
            │
            ▼
┌─── Step 1: applyDefaultRole(userId, defaultRoleKey) ─────────┐
│   查 sys_user_role：已有角色？→ 跳过                            │
│   无角色 → 按 defaultRoleKey 查 sys_role → 写入 sys_user_role   │
│   仅当 SsoClientRoleProvider Bean 存在时执行                    │
└──────────────────────────────────────────────────────────────┘
            │
            ▼
┌─── Step 2: queryIsSuperAdmin(userId) ────────────────────────┐
│   向 Server 发送 QUERY_USER_ROLES { centerId, clientId }     │
│   Server 查 sso_user_client_role 表 → 返回 true / false      │
│   失败降级为 false（不中断登录）                                │
└──────────────────────────────────────────────────────────────┘
            │
            ▼
┌─── Step 3: applySuperAdmin(userId, isSuperAdmin) ────────────┐
│   每次登录都执行，确保与平台一致                                 │
│   写入本地 DB（user_tag_cd、超管角色绑定等）                    │
│   仅当 SsoRoleBindingService Bean 存在时执行                   │
└──────────────────────────────────────────────────────────────┘
            │
            ▼
┌─── Step 4: buildLoginUser(userId) ───────────────────────────┐
│   从本地 DB 构建完整用户对象                                    │
│   此时 DB 已包含 Step 1 的默认角色 + Step 3 的超管状态          │
│   roles、permissions、depts、dataScope 等一次性读取             │
└──────────────────────────────────────────────────────────────┘
            │
            ▼
┌─── Step 5: createSession(user, parameter, loginId) ──────────┐
│   SsoSessionCreator 建立 Session                              │
│   Starter 额外写入 isSuperAdmin Boolean 到 TokenSession       │
│   返回 accessToken + expireIn + userInfo                      │
└──────────────────────────────────────────────────────────────┘
```

**为什么先写 DB 再构建用户？** `buildLoginUser()` 从 DB 完整构建用户信息（角色、权限、部门、数据范围等），这些字段之间存在关联。如果在构建之后手动修改 `roles`，关联的 `permissions`、`dataScope` 等不会自动更新。先写 DB 再构建，让所有数据自然保持一致。

---

## 降级与容错

| 场景                                     | 处理策略                        | 影响                                   |
| ---------------------------------------- | ------------------------------- | -------------------------------------- |
| Server 不可达（`QUERY_USER_ROLES` 失败） | `isSuperAdmin` 降级为 `false`   | 用户以非超管身份登录，不中断流程       |
| `applyDefaultRole` 异常                  | catch + warn 日志，继续后续步骤 | 首次登录可能无默认角色，但登录不受影响 |
| `applySuperAdmin` 异常                   | catch + warn 日志，继续后续步骤 | 本地超管状态可能未更新，但登录不受影响 |
| `SsoSyncHelper.syncSuperAdmin` 失败      | `@Async` + catch + warn 日志    | Client 本地变更已生效，Server 暂未同步 |

核心原则：**登录流程不因任何可选步骤的失败而中断**。失败时降级为安全默认值（非超管）。

---

## 参考手册

### SPI 接口一览

| 接口                    | 泛型               | 必须   | 内置默认实现                                   | 不实现的后果                                        |
| ----------------------- | ------------------ | ------ | ---------------------------------------------- | --------------------------------------------------- |
| `SsoUserMappingService` | 无                 | **是** | 无                                             | 自动装配不激活                                      |
| `SsoLoginHandler<U>`    | `U` = 用户对象类型 | **是** | 无                                             | 自动装配不激活                                      |
| `SsoSessionCreator<U>`  | `U` = 用户对象类型 | 否     | `DefaultSsoSessionCreator`                     | 只执行 `StpUtil.login`，TokenSession 不写入用户对象 |
| `SsoClientRoleProvider` | 无                 | 否     | 无                                             | 首次登录默认角色初始化跳过                          |
| `SsoRoleBindingService` | 无                 | 否     | `DefaultSsoRoleBindingService`（仅 warn 日志） | 默认角色不写入 DB，超管状态不同步到 DB              |

> `SsoLoginHandler<U>` 与 `SsoSessionCreator<U>` 的泛型 `U` 必须是同一类型。

---

### 自动注册的端点

路径前缀 `/sso`，已标注 `@SaIgnore`，不会被 Sa-Token 拦截：

| 方法  | 路径                   | 说明                      |
| ----- | ---------------------- | ------------------------- |
| `GET` | `/sso/isLogin`         | 查询当前登录状态          |
| `GET` | `/sso/getSsoAuthUrl`   | 生成 SSO 认证中心跳转地址 |
| `GET` | `/sso/doLoginByTicket` | 凭 ticket 完成本地登录    |
| `ANY` | `/sso/logout`          | 发起单点注销              |
| `ANY` | `/sso/logoutCall`      | 接收 Server 单点注销回调  |
| `ANY` | `/sso/pushC`           | 接收 Server 消息推送      |

---

### 工具类

#### `SsoClientUtil`

```java
// 判断当前登录用户是否为平台指定的超管
boolean isSuperAdmin = SsoClientUtil.isSuperAdmin();
```

该值在每次 SSO 登录时从 Server 查询并写入 TokenSession。未登录或查询失败时返回 `false`。

#### `SsoSyncHelper`

```java
@Autowired(required = false)
private SsoSyncHelper ssoSyncHelper;

// Client 内部变更超管后，通知 Server 同步
ssoSyncHelper.syncSuperAdmin(localUserId, true);   // 赋予
ssoSyncHelper.syncSuperAdmin(localUserId, false);  // 撤销
```

`@Async` 异步执行，失败仅 warn 日志。非 SSO 环境下此 Bean 不存在，注入时用 `@Autowired(required = false)`。

#### `SsoUserMetaUtils`

```java
// 从 Server 推送的注册消息中解析用户元信息
SsoUserMeta meta = SsoUserMetaUtils.fromEntries(message.getDataMap().entrySet());
```

`SsoUserMeta` 字段：

| 字段         | 类型            | 说明               |
| ------------ | --------------- | ------------------ |
| `ssoUserId`  | `Long`          | SSO Server 用户 ID |
| `username`   | `String`        | 用户名             |
| `nickname`   | `String`        | 昵称               |
| `email`      | `String`        | 邮箱               |
| `phone`      | `String`        | 手机号             |
| `avatarUrl`  | `String`        | 头像地址           |
| `createTime` | `LocalDateTime` | 注册时间           |

---

### 消息常量

```java
SsoCoreConstant.MESSAGE_REGISTER           // "REGISTER"           — Server 推送用户注册同步
SsoCoreConstant.MESSAGE_USER_CHECK         // "USER_CHECK"         — 用户信息查询（降级）
SsoCoreConstant.MESSAGE_QUERY_USER_ROLES   // "QUERY_USER_ROLES"   — 登录时查询超管状态
SsoCoreConstant.MESSAGE_SYNC_SUPER_ADMIN   // "SYNC_SUPER_ADMIN"   — Client 回传超管变更
SsoCoreConstant.SESSION_KEY_IS_SUPER_ADMIN // "isSuperAdmin"       — TokenSession key（Boolean）
```

---

### 响应格式

端点统一使用 `SsoApiResult<T>` 包装：

```json
{ "code": "0000", "message": "SUCCESS", "data": { ... }, "param": {} }
```

| 字段      | 类型     | 说明            |
| --------- | -------- | --------------- |
| `code`    | `String` | `"0000"` = 成功 |
| `message` | `String` | 响应信息        |
| `data`    | `T`      | 业务数据        |
| `param`   | `Object` | 扩展参数        |

---

### 自动装配条件

全部满足时才激活，否则对应用零影响：

| 条件                                              | 说明                            |
| ------------------------------------------------- | ------------------------------- |
| `@ConditionalOnClass(SaSsoClientTemplate.class)`  | classpath 中存在 `sa-token-sso` |
| `@ConditionalOnBean(SsoUserMappingService.class)` | 业务方已提供实现                |
| `@ConditionalOnBean(SsoLoginHandler.class)`       | 业务方已提供实现                |

---

## 最小化接入示例

不依赖数据库，仅用内存 Map 模拟，适合快速验证联通性：

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

// 2. 构建用户对象（直接返回 userId）
@Component
public class MinimalLoginHandler implements SsoLoginHandler<Long> {

    @Override
    public Long buildLoginUser(Long userId) {
        return userId;
    }
}

// 只实现以上 2 个必须接口即可启动
// · SsoSessionCreator → DefaultSsoSessionCreator 自动生效
// · SsoClientRoleProvider → 不实现，首次登录默认角色跳过
// · SsoRoleBindingService → DefaultSsoRoleBindingService 自动生效（仅 warn 日志）
// · 超管状态仍会从 Server 查询并存入 TokenSession
// · SsoClientUtil.isSuperAdmin() 可正常使用
```

---

## 本地调试

1. IDEA 中打开主项目
2. `File → Project Structure → Modules → + → Import Module`
3. 选择 `sz-sso-client-starter` 目录，以 Maven 模块方式导入
4. 消费方 `pom.xml` 中确保依赖版本为 `1.0.0`
5. 修改 Starter 后无需 `mvn install`，增量编译即可生效

---

## 版本要求

| 依赖        | 版本   |
| ----------- | ------ |
| Java        | 21+    |
| Spring Boot | 3.x    |
| Sa-Token    | 1.44.0 |

---

## Server 端配套说明

> 具体实现见 SSO Server 项目文档，此处仅说明概念。

**数据库**

```sql
CREATE TABLE sso_user_client_role (
    id          bigint      PRIMARY KEY AUTO_INCREMENT,
    center_id   bigint      NOT NULL COMMENT 'SSO Server 用户 ID',
    client_id   varchar(64) NOT NULL COMMENT '对应 sso_client.client_id',
    create_time datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  bigint      COMMENT '操作的平台管理员 ID',
    UNIQUE KEY uk_user_client (center_id, client_id)
);
```

**消息处理器**

| 消息类型           | 方向            | 处理逻辑                                                     |
| ------------------ | --------------- | ------------------------------------------------------------ |
| `QUERY_USER_ROLES` | Client → Server | 查 `sso_user_client_role` 表，返回 `isSuperAdmin: true/false` |
| `SYNC_SUPER_ADMIN` | Client → Server | `true` 时 upsert 记录，`false` 时 delete 记录                |

**管理端**

平台管理员通过管理端 UI 维护各 Client 的超管名单（增加/撤销），这是整个超管体系的起点。
