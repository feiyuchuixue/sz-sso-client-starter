# sz-sso-client-starter

基于 [Sa-Token SSO 模式三](https://sa-token.cc/doc.html#/sso/sso-type3) 封装的 Spring Boot Starter，让业务服务只需实现 3 个 SPI 接口，即可快速接入 SSO 单点登录。

## 特性

- **零侵入**：starter 自动装配所有 SSO 端点，业务代码无需关心 Sa-Token SSO 内部细节
- **类型安全**：泛型 SPI 设计，`buildLoginUser` / `createSession` 的用户类型 `<U>` 全程一致
- **可覆盖**：所有内置 Bean 均使用 `@ConditionalOnMissingBean`，业务方可按需覆盖
- **框架无关**：响应 DTO（`SsoApiResult`、`SsoLoginResult`）不依赖任何业务框架
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

> starter 已内置 `sa-token-sso`、`sa-token-redis-template`、`sa-token-forest`、`commons-pool2`、`spring-boot-starter-web`，无需重复引入。

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

#### 3.1 `SsoUserMappingService` — 用户 ID 映射（**必须实现**）

负责 SSO Server 的 `centerId`（SSO 用户 ID）与本地 `loginId` 之间的双向映射，以及用户注册同步。

```java
@Component
@RequiredArgsConstructor
public class SsoUserMappingServiceImpl implements SsoUserMappingService {

    private final SysUserService userService;

    /**
     * 本地 userId → SSO centerId
     * 用于单点注销时，将本地 loginId 转换为 SSO Server 的 centerId
     */
    @Override
    public Object toServerUserId(Object clientUserId) {
        return userService.getSsoCenterId(Long.valueOf(clientUserId.toString()));
    }

    /**
     * SSO centerId → 本地 userId
     * ticket 验证通过后，将 SSO centerId 转换为本地 userId（如不存在则先创建）
     */
    @Override
    public Object toClientUserId(Object serverUserId) {
        return userService.findOrCreateByCenterId(Long.valueOf(serverUserId.toString()));
    }

    /**
     * 处理 SSO Server 推送的用户注册消息
     * 在此完成本地用户数据的初始化（用户名、昵称、手机号等）
     */
    @Override
    public void syncSsoRegisterUser(SaSsoMessage message) {
        SsoUserMeta meta = SsoUserMetaUtils.fromEntries(message.getDataMap().entrySet());
        userService.syncFromSso(meta);
    }
}
```

#### 3.2 `SsoLoginHandler<U>` — 构建用户对象（**必须实现**）

ticket 验证成功、`toClientUserId` 转换完成后，starter 用本地 `userId` 调用此方法获取完整用户对象（含权限、角色等）。

```java
@Component
@RequiredArgsConstructor
public class SsoLoginHandlerImpl implements SsoLoginHandler<LoginUser> {

    private final SysUserService userService;

    @Override
    public LoginUser buildLoginUser(Long userId) {
        // 调用业务层方法，构建包含权限/角色的用户对象
        return userService.buildLoginUser(userId);
    }
}
```

> `LoginUser` 是你的业务框架用户类型，与 `SsoSessionCreator<U>` 的泛型参数保持一致即可。

#### 3.3 `SsoSessionCreator<U>` — 建立本地会话（**可选覆盖**）

starter 内置 `DefaultSsoSessionCreator`（调用 `StpUtil.login` 后直接返回 token）。  
如果你的框架需要在会话中额外存储用户信息（如将 `LoginUser` 写入 TokenSession），覆盖此接口：

```java
@Component
public class SsoSessionCreatorImpl implements SsoSessionCreator<LoginUser> {

    @Override
    public SsoLoginResult createSession(LoginUser loginUser, SaLoginParameter parameter, Object loginId) {
        // 使用你的框架登录工具，将 loginUser 存入 TokenSession
        LoginUtils.performLogin(loginUser, parameter, null);

        String accessToken = StpUtil.getTokenValue();
        long expireIn = StpUtil.getTokenTimeout();
        return SsoLoginResult.of(accessToken, expireIn, loginUser);
    }
}
```

> 如果你直接使用 Sa-Token 原生 API 且无需在 TokenSession 中存额外数据，**无需实现此接口**，starter 内置的 `DefaultSsoSessionCreator` 会自动生效。

---

### 第四步：验证自动装配

启动应用后，日志中出现以下输出即说明接入成功：

```
SSO Client 自动配置完成: 策略函数和消息处理器已注册
SSO Client 自动配置: 注册 SsoClientService
```

---

## 自动注册的端点

starter 自动注册以下 REST 端点（路径前缀 `/sso`），**无需任何额外配置**，也不会被 Sa-Token 拦截器拦截（已标注 `@SaIgnore`）：

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/sso/isLogin` | 查询当前登录状态，返回 `hasLogin`、`loginId` |
| `GET` | `/sso/getSsoAuthUrl` | 根据 `clientLoginUrl` 参数生成 SSO 认证中心跳转地址 |
| `GET` | `/sso/doLoginByTicket` | 凭 `ticket` 完成本地登录，返回 `accessToken` 及用户信息 |
| `ANY` | `/sso/logout` | 发起单点注销（重定向到 SSO Server 执行全局下线） |
| `ANY` | `/sso/logoutCall` | 接收 SSO Server 下发的单点注销回调 |
| `ANY` | `/sso/pushC` | 接收 SSO Server 推送的消息（如用户注册同步） |

---

## 响应格式

业务端点统一使用 `SsoApiResult<T>` 包装，与前端 axios 拦截器期望的格式完全兼容：

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

## SPI 接口一览

| 接口 | 泛型 | 是否必须 | 内置默认实现 | 说明 |
|------|------|---------|-------------|------|
| `SsoUserMappingService` | 无 | **必须** | 无 | centerId ↔ loginId 双向映射 + 用户注册同步 |
| `SsoLoginHandler<U>` | `U` = 用户对象类型 | **必须** | 无 | 根据本地 userId 构建用户对象 |
| `SsoSessionCreator<U>` | `U` = 用户对象类型 | 可选 | `DefaultSsoSessionCreator` | 建立本地会话，返回 `SsoLoginResult` |

> `SsoLoginHandler<U>` 与 `SsoSessionCreator<U>` 的泛型参数 `U` 必须是**同一类型**。

---

## 工具类

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
SsoCoreConstant.MESSAGE_REGISTER    // "REGISTER"   — 用户注册同步消息类型
SsoCoreConstant.MESSAGE_USER_CHECK  // "USER_CHECK" — 用户信息查询消息类型（降级）
```

---

## 自动装配条件

starter 的自动配置只在满足以下所有条件时才会激活，**否则对应用零影响**：

| 条件 | 说明 |
|------|------|
| `@ConditionalOnClass(SaSsoClientTemplate.class)` | classpath 中存在 `sa-token-sso` |
| `@ConditionalOnBean(SsoUserMappingService.class)` | 业务方已提供 `SsoUserMappingService` Bean |
| `@ConditionalOnBean(SsoLoginHandler.class)` | 业务方已提供 `SsoLoginHandler` Bean |

---

## 最小化接入示例

以下是一个不依赖数据库、仅用内存 Map 模拟的最小化接入（适合快速验证联通性）：

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
```

---

## 本地调试（IDEA Import Module）

1. 在 IDEA 中打开你的主项目
2. `File → Project Structure → Modules → + → Import Module`
3. 选择 `sz-sso-client-starter` 目录，以 Maven 模块方式导入
4. 在消费方 `pom.xml` 中确保依赖版本为 `1.0.0`
5. IDEA 会自动通过本地源码解析，修改 starter 后无需 `mvn install`，增量编译即可生效

---

## 版本要求

| 依赖 | 版本 |
|------|------|
| Java | 21+ |
| Spring Boot | 3.x |
| Sa-Token | 1.44.0 |
