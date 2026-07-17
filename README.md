# sz-sso Client SDK 统一接入指南

本文是一篇面向接入方的完整指南，覆盖 **Java 后端 Client Starter** 与 **Web/Vue 前端 Client SDK** 的接入顺序、最小配置、扩展点和联调验收。你可以把它当作一份从 0 开始接入 sz-sso 的操作手册。

当前文档基于以下实际代码编写：

| 类型 | 工程 | 说明 |
| --- | --- | --- |
| Java Client SDK | `sz-sso-client-starter` | Spring Boot 后端接入 SDK |
| Web Client SDK | `sz-sso-client-web-sdk` | Vue 前端轻量接入 SDK |
| Java 接入示例 | `sz-sso-client-demo` | 后端 Client 示例 |
| Web 接入示例 | `sz-sso-client-web-demo` | 前端 Client 示例 |

> 本文只说明当前已经落地的 Client 接入能力。OAuth、OIDC、SAML、LDAP、AD、MFA、第三方 Provider 等企业级协议或账号治理能力不属于当前开源版 Client SDK 已完成能力。

## 一、先理解整体流程

sz-sso 的 Client 接入分为两部分：

1. **后端 Client**：接收前端带回来的 `ticket`，调用 SSO Server 换取登录信息，并在本系统写入登录态。
2. **前端 Client**：负责跳转认证中心、处理 `/sso-login` 回调、拿 `ticket` 调后端换取本系统 token。

一次完整登录流程如下：

```text
用户访问业务系统
  -> 前端发现未登录，跳转 /sso-login?back=原始地址
  -> Web SDK 判断当前没有 ticket，拼出认证中心登录地址
  -> 用户在认证中心登录
  -> 认证中心回跳业务前端 /sso-login?ticket=xxx&back=xxx
  -> Web SDK 调 Client 后端 /sso/doLoginByTicket
  -> Java Starter 调 SSO Server 校验 ticket
  -> Java Starter 映射本地用户、写入本地登录态
  -> 后端返回本系统 accessToken
  -> 前端保存 token，并回到 back 地址
```

你接入时只需要记住一句话：

> **前端负责跳转和拿票，后端负责验票和落本地登录态。**

## 二、接入优先级

建议按下面顺序接入。不要一上来就做角色同步、用户菜单、扩展消息，否则排查成本会很高。

| 优先级 | 目标 | 必做内容 | 完成信号 |
| --- | --- | --- | --- |
| P0 | 跑通统一登录 | 后端 Starter、前端 SDK、`/sso-login`、`ticket` 换 token | 用户能从业务系统跳到认证中心，登录后回到业务系统并登录成功 |
| P1 | 补齐业务权限 | 默认角色、Client 超管、退出清理、用户菜单 | 登录后角色正确，超管识别正确，退出后本地状态清空 |
| P2 | 扩展治理能力 | 自定义消息、更多用户同步、诊断能力 | 能按业务需要扩展 SSO Server 与 Client 之间的消息 |

如果你是第一次接入，只需要先完成 **P0**。

## 三、接入前准备

开始前先确认这些信息。信息不完整时，后面的配置会不知道该填什么。

| 配置项 | 示例 | 说明 |
| --- | --- | --- |
| Client 标识 | `sso-client3` | 当前业务系统在 SSO Server 中登记的 Client 标识 |
| SSO Server API 地址 | `http://127.0.0.1:5001/api/sso` | Java Starter 调用 SSO Server 的后端接口地址 |
| 认证中心前端地址 | `http://127.0.0.1:3310` | 浏览器跳转登录页的地址 |
| Client 后端 API 前缀 | `/api` | Web SDK 调用业务后端时使用的基础路径 |
| Client 后端推送接收地址 | `http://127.0.0.1:5001/api/sso/pushS` | 当前 Client 后端暴露给 SSO Server 的消息推送地址 |
| 登录回调路径 | `/sso-login` | 前端 SDK 默认回调路由 |

示例 demo 中使用的关键值：

```yaml
client: sso-client3
server-url: http://127.0.0.1:5001/api/sso
auth-url: http://127.0.0.1:3310/login
push-url: http://127.0.0.1:5001/api/sso/pushS
```

前端 demo 中使用：

```env
VITE_API_CONTEXT_PATH=/api
VITE_API_PROXY_TARGET=http://127.0.0.1:9991
VITE_SSO_CLIENT_FLAG=sso-client3
VITE_UCENTER_URL=http://127.0.0.1:3310
```

## 四、P0：后端接入 Java Client Starter

后端接入的目标是：让业务系统拥有 `/sso/doLoginByTicket` 等端点，并能把 SSO 用户转换成本系统登录用户。

### 4.1 添加 Maven 依赖

在业务后端工程中引入：

```xml
<dependency>
    <groupId>com.sz</groupId>
    <artifactId>sz-sso-client-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

如果你的工程没有直接引入 core，也可以按 demo 的方式同时引入：

```xml
<dependency>
    <groupId>com.sz</groupId>
    <artifactId>sz-sso-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 4.2 配置 Sa-Token SSO Client

在业务系统的配置文件中增加 `sa-token.sso-client`：

```yaml
sa-token:
  sso-client:
    # Client 标识，必须与 SSO Server 中登记的 client 一致
    client: sso-client3

    # SSO Server 后端接口地址。Starter 会用它校验 ticket、获取用户信息
    server-url: http://127.0.0.1:5001/api/sso

    # 认证中心前端登录页地址。后端 /sso/getSsoAuthUrl 会基于它生成跳转地址
    auth-url: http://127.0.0.1:3310/login

    # 是否向 SSO Server 注册单点登出回调
    reg-logout-call: true

    # 当前 Client 后端暴露给 SSO Server 的推送地址
    # 注意：这里是 Server 调 Client 的地址，demo 中是 /api/sso/pushS
    push-url: http://127.0.0.1:5001/api/sso/pushS
```

常见误区：

| 容易填错的地方 | 正确写法 |
| --- | --- |
| `server-url` 写成认证中心前端地址 | 应写 SSO Server 后端 API 地址 |
| `auth-url` 写成后端 API 地址 | 应写认证中心前端登录页地址 |
| 把消息接收入口误当推送登记地址 | 应写当前 Client 提供给 SSO Server 调用的推送入口，demo 是 `/api/sso/pushS` |
| 使用历史配置名 | 当前配置项是 `reg-logout-call` |

### 4.3 实现用户映射：`SsoUserMappingService`

这是后端接入的第一个必选扩展点。

SSO Server 认识的是统一身份用户，本业务系统认识的是本地用户。`SsoUserMappingService` 的作用就是在两者之间做转换。

接口定义：

```java
public interface SsoUserMappingService {

    Object toServerUserId(Object clientUserId);

    /**
     * 只做 SSO Server 用户 ID 到 Client 本地用户 ID 的 mapping 查询。
     * 查不到建议返回 null；不要在这里调用 USER_CHECK、补 mapping 或创建本地用户。
     */
    Object toClientUserId(Object serverUserId);

    /**
     * 身份同步、超管同步、角色同步使用。
     * 可以查询中心用户元信息并唯一匹配已有本地用户后补 mapping，但不应创建用户。
     */
    default Object resolveExistingClientUser(Object serverUserId) {
        return toClientUserId(serverUserId);
    }

    /**
     * 登录票据转换使用。
     * 可以先唯一匹配已有本地用户并补 mapping，必要时再创建本地用户。
     */
    default Object resolveOrProvisionClientUser(Object serverUserId) {
        return toClientUserId(serverUserId);
    }

    void syncSsoRegisterUser(SaSsoMessage message, String client);
}
```

你需要实现五个动作：

| 方法 | 什么时候用 | 你要做什么 |
| --- | --- | --- |
| `toServerUserId` | 本地用户同步到 SSO 时 | 根据本地用户 ID 找到对应的 SSO 用户 ID |
| `toClientUserId` | Sa-Token / Starter 基础 ID 映射查询 | 只查映射；查不到返回 `null`；不要创建本地用户 |
| `resolveExistingClientUser` | 超管同步、身份同步、角色同步 | 缺映射时可查询中心用户元信息，唯一匹配已有本地用户后补 mapping；不要创建用户 |
| `resolveOrProvisionClientUser` | SSO 登录回业务系统时 | 先查映射，再唯一匹配已有用户并补 mapping；没有匹配且业务允许时才创建本地用户 |
| `syncSsoRegisterUser` | SSO Server 推送新注册用户时 | 使用 provisioning lock，先关联已有本地用户，再按需创建本地用户 |

示例写法：

```java
@Service
public class DemoSsoUserMappingService implements SsoUserMappingService {

    private final DemoUserService demoUserService;

    public DemoSsoUserMappingService(DemoUserService demoUserService) {
        this.demoUserService = demoUserService;
    }

    @Override
    public Object toServerUserId(Object clientUserId) {
        return demoUserService.findServerUserIdByClientUserId(clientUserId);
    }

    @Override
    public Object toClientUserId(Object serverUserId) {
        return demoUserService.findMappedClientUserIdByServerUserId(serverUserId);
    }

    @Override
    public Object resolveExistingClientUser(Object serverUserId) {
        return demoUserService.resolveExistingClientUserId(serverUserId);
    }

    @Override
    public Object resolveOrProvisionClientUser(Object serverUserId) {
        return demoUserService.resolveOrProvisionClientUserId(serverUserId);
    }

    @Override
    public void syncSsoRegisterUser(SaSsoMessage message, String client) {
        demoUserService.syncRegisterUserFromSsoMessage(message, client);
    }
}
```

实现建议：

- `toClientUserId` 只查询映射关系；查不到返回 `null`。
- 登录自动开户逻辑放到 `resolveOrProvisionClientUser`。
- 超管同步、角色同步等身份同步兜底逻辑放到 `resolveExistingClientUser`。
- 已存在本地用户时，优先复用本地用户，不要重复创建。
- 创建本地用户前，先按业务系统自己的唯一规则匹配已有用户。
- 创建本地用户或命中已有用户后，要保存 SSO 用户 ID 与本地用户 ID 的映射关系。
- 如果你暂时没有用户表扩展字段，可以先建一张映射表保存关系。
- 登录创建和注册同步建议使用同一把 provisioning lock：`sso:user:provision:{client}:{ssoUserId}`，并在锁内 double-check mapping。
- 历史 Client 如果把创建逻辑放在 `toClientUserId`，应迁移到 `resolveOrProvisionClientUser`；否则超管同步、角色同步等身份同步场景可能重复开户。

### 4.4 实现登录适配：`SsoClientLoginAdapter`

这是后端接入的第二个必选扩展点。

Starter 负责完成 SSO 协议流程，但它不知道你的系统使用什么登录用户对象，也不知道你的系统返回给前端的 token 结构。因此需要你实现 `SsoClientLoginAdapter`。

接口定义：

```java
public interface SsoClientLoginAdapter<U> {

    U buildLoginUser(Long userId);

    Object createLoginResult(U user, SaLoginParameter parameter, Object loginId);
}
```

两个方法分别做：

| 方法 | 你要做什么 |
| --- | --- |
| `buildLoginUser` | 根据本地用户 ID 查询并构建业务系统自己的登录用户对象 |
| `createLoginResult` | 生成返回给前端的登录结果，通常包含 `accessToken` |

示例写法：

```java
@Service
public class DemoSsoClientLoginAdapter implements SsoClientLoginAdapter<DemoLoginUser> {

    private final DemoUserService demoUserService;

    public DemoSsoClientLoginAdapter(DemoUserService demoUserService) {
        this.demoUserService = demoUserService;
    }

    @Override
    public DemoLoginUser buildLoginUser(Long userId) {
        return demoUserService.buildLoginUser(userId);
    }

    @Override
    public Object createLoginResult(DemoLoginUser user, SaLoginParameter parameter, Object loginId) {
        return Map.of(
            "accessToken", StpUtil.getTokenValue(),
            "userId", user.getUserId(),
            "username", user.getUsername()
        );
    }
}
```

前端 SDK 默认会读取后端返回数据中的 `accessToken`：

```ts
onLoginSuccess: data => {
  userStore.setToken(data.accessToken);
}
```

所以如果你不改前端逻辑，后端返回结果里应包含 `accessToken`。

### 4.5 Starter 自动提供的端点

引入 Starter 后，Client 后端会提供这些端点：

| 端点 | 调用方 | 作用 |
| --- | --- | --- |
| `GET /sso/isLogin` | 前端或调试工具 | 判断当前是否登录 |
| `GET /sso/getSsoAuthUrl` | 前端或调试工具 | 获取认证中心登录地址 |
| `GET /sso/doLoginByTicket` | Web SDK | 使用 `ticket` 完成 SSO 登录 |
| `GET /sso/getSsoPortalUrl` | Web SDK / 用户菜单 | 为当前已登录 Client 用户申请认证中心个人门户一次性入口 |
| `POST /sso/logout` | 业务前端 | 退出当前 Client 登录态，并联动 SSO 登出逻辑 |
| `/sso/logoutCall` | SSO Server | 单点登出回调 |
| `/sso/pushC` | SSO Server | Sa-Token SSO Client 消息接收入口 |
| `POST /sso/v1/login/transactions` | Web SDK | 创建浏览器绑定的 CAP 登录事务 |
| `POST /sso/v1/login/callback` | Web SDK | 校验浏览器绑定并交换一次性 Login Ticket |
| `POST /sso/v1/callbacks/logout` | SSO Server | 正式 CAP V1 单点注销回调 |
| `POST /sso/v1/messages` | SSO Server | 正式 CAP V1 业务消息入口 |

> 在常规前端接入中，Web SDK 最关键调用的是 `/sso/doLoginByTicket`。业务系统自己的退出按钮应调用 `/sso/logout`。头像菜单进入认证中心个人门户时，应调用 `/sso/getSsoPortalUrl`，不要直接拼认证中心 `/ucenter/*` 地址。

### 4.6 后端登录内部流程

理解这个流程有助于你排查问题：

```text
/sso/doLoginByTicket
  -> 校验 ticket
  -> 从 SSO Server 拿到 SSO 用户信息
  -> SsoUserMappingService.resolveOrProvisionClientUser
  -> 应用默认角色
  -> 查询并同步 Client 超管身份
  -> SsoClientLoginAdapter.buildLoginUser
  -> Sa-Token 写入登录态
  -> SsoClientLoginAdapter.createLoginResult
  -> 写入 TokenSession
  -> 返回登录结果给前端
```

如果登录失败，优先排查：

1. `ticket` 是否传到 `/sso/doLoginByTicket`。
2. `server-url` 是否能访问 SSO Server。
3. `client` 是否与 SSO Server 登记一致。
4. `SsoUserMappingService.resolveOrProvisionClientUser` 是否正确返回本地用户 ID。
5. `SsoClientLoginAdapter.createLoginResult` 是否返回了前端需要的 `accessToken`。

### 4.7 单实例与多实例状态

Starter 不再使用 `HttpSession ID` 绑定 CAP 登录事务，而是写入随机的 `SZ_SSO_BROWSER` 浏览器会话 Cookie：`HttpOnly`、`SameSite=Lax`、`Path=/`，HTTPS 请求自动增加 `Secure`。Cookie 只用于证明登录事务属于同一浏览器，不是登录凭据。

Starter 会自动选择状态后端：

| 环境 | 状态后端 | 行为 |
| --- | --- | --- |
| 容器存在 `StringRedisTemplate` | Spring Data Redis | 登录事务、Nonce、SLO/Message 幂等跨实例共享 |
| `local/dev/test` 且没有 Redis Bean | JVM 内存 | 允许单实例开发，启动输出强警告 |
| `prod/production/preview` 且没有共享仓库 | 无 | Spring Context 启动失败，禁止静默降级 |

多实例生产部署不需要新增 `sz.sso.client-access` 配置，也不要求负载均衡启用粘性会话；需要保证所有实例使用同一逻辑 Redis，并确保 Client 自身的 Sa-Token 登录态也使用共享 Dao。

如果不使用 Redis，可以提供自定义 `ClientAccessStateRepository` Bean，但它必须实现跨实例可见的原子 `put-if-absent`、CAS、Compare-and-delete 和 TTL。存储不可用时必须失败关闭，不能切回本地 Map。

## 五、P0：前端接入 Web Client SDK

前端接入的目标是：未登录时进入 `/sso-login`，SDK 自动跳认证中心；认证中心回跳后，SDK 自动拿 `ticket` 调后端换 token。

### 5.1 安装依赖

在业务前端工程中安装：

```bash
pnpm add sz-sso-client-web-sdk
```

demo 开发阶段使用本地依赖：

```json
{
  "dependencies": {
    "sz-sso-client-web-sdk": "file:../../sso/sz-sso-client-web-sdk"
  }
}
```

### 5.2 创建 SSO Client

在前端入口文件中创建 SDK Client：

```ts
import { createSsoClient } from 'sz-sso-client-web-sdk';
import { createSsoPlugin } from 'sz-sso-client-web-sdk/vue';
import 'sz-sso-client-web-sdk/style.css';

const ssoClient = createSsoClient({
  clientFlag: import.meta.env.VITE_SSO_CLIENT_FLAG || 'sso-client3',
  ssoClientApiBaseUrl: import.meta.env.VITE_API_CONTEXT_PATH || '/api',
  authCenterBaseUrl: import.meta.env.VITE_UCENTER_URL,
  onLoginSuccess: data => {
    userStore.setToken(data.accessToken);
  },
});

app.use(createSsoPlugin(ssoClient));
```

关键点：

| 配置 | 示例 | 说明 |
| --- | --- | --- |
| `clientFlag` | `sso-client3` | 与后端 `sa-token.sso-client.client` 保持一致 |
| `ssoClientApiBaseUrl` | `/api` | Web SDK 请求 Client 后端的基础路径 |
| `authCenterBaseUrl` | `http://127.0.0.1:3310` | 认证中心前端地址 |
| `onLoginSuccess` | `userStore.setToken(data.accessToken)` | 换票成功后，把后端 token 写入业务系统状态 |

### 5.3 注册 SDK 路由

在你的静态路由中加入：

```ts
import { getSsoRoutes } from 'sz-sso-client-web-sdk/vue';

export const staticRouter = [
  // 你的业务路由
  ...getSsoRoutes(),
];
```

SDK 默认提供两个路由：

| 路由 | 作用 |
| --- | --- |
| `/sso-login` | SSO 登录回调页，也是业务系统触发 SSO 登录的入口 |
| `/sso-forbidden` | SSO 登录成功但无权限时的兜底页 |

### 5.4 加入路由白名单

如果你的项目有路由守卫，需要把 SDK 路由加入白名单：

```ts
import {
  SSO_CALLBACK_PATH,
  SSO_FORBIDDEN_PATH,
} from 'sz-sso-client-web-sdk/vue';

export const LOGIN_WHITE_LIST = [
  '/login',
  SSO_CALLBACK_PATH,
  SSO_FORBIDDEN_PATH,
];
```

不加白名单时，常见现象是：

- `/sso-login` 还没来得及处理 `ticket`，就被业务路由守卫拦回 `/login`。
- 认证中心登录成功后，前端页面来回跳转。

### 5.5 业务登录页跳转 `/sso-login`

业务系统的登录页不需要自己拼认证中心地址，只需要跳 SDK 回调页：

```ts
router.push({
  path: '/sso-login',
  query: {
    back: route.query.redirect || '/',
  },
});
```

SDK 会自动判断：

| 当前 URL 状态 | SDK 行为 |
| --- | --- |
| 没有 `ticket` | 生成认证中心登录地址并跳转 |
| 有 `ticket` | 调用后端 `/sso/doLoginByTicket` 换 token |
| 后端返回 `O4031` | 跳转 `/sso-forbidden` |
| 登录成功 | 执行 `onLoginSuccess`，再回到 `back` 地址 |

### 5.6 退出登录

退出登录由业务系统主动完成。推荐顺序：

1. 调用 Client 后端 `/sso/logout`。
2. 清理前端 token。
3. 清理用户信息、菜单、按钮权限、动态路由。
4. 断开 WebSocket 或其他长连接。
5. 跳回业务登录页或首页。

示例：

```ts
await logoutApi();
userStore.setToken('');
userStore.setUserInfo(null);
authStore.clearAuth();
websocket.close();
router.replace('/login');
```

后端 demo 的退出接口是：

```ts
export const logoutApi = () => {
  return http.post('/sso/logout');
};
```

## 六、P0 最小联调清单

完成 P0 后，按下面顺序验证。

### 6.1 后端检查

| 检查项 | 预期 |
| --- | --- |
| 后端启动成功 | 没有缺少 `SsoUserMappingService` 或 `SsoClientLoginAdapter` 的 Bean 错误 |
| 访问 `/sso/isLogin` | 能返回 SDK 标准响应 |
| 访问 `/sso/getSsoAuthUrl` | 返回的地址指向认证中心登录页 |
| 配置 `server-url` | 能连通 SSO Server 后端 API |
| 配置 `push-url` | 指向当前 Client 可被 SSO Server 访问的推送地址 |

### 6.2 前端检查

| 检查项 | 预期 |
| --- | --- |
| `/sso-login` 路由存在 | 访问时不会 404 |
| `/sso-login` 在白名单中 | 不会被路由守卫拦截 |
| 未带 `ticket` 访问 `/sso-login` | 自动跳认证中心 |
| 带 `ticket` 回跳 `/sso-login` | 调用 Client 后端 `/sso/doLoginByTicket` |
| `onLoginSuccess` 执行 | 前端 token 被写入 store |

### 6.3 端到端检查

最小成功路径：

1. 清空浏览器本业务系统 token。
2. 访问业务系统受保护页面。
3. 前端跳到业务登录页或直接跳 `/sso-login`。
4. SDK 跳到认证中心。
5. 用户在认证中心登录。
6. 认证中心回跳 `/sso-login?ticket=xxx&back=xxx`。
7. 前端调用 `/api/sso/doLoginByTicket?ticket=xxx`。
8. 后端返回包含 `accessToken` 的登录结果。
9. 前端保存 token。
10. 页面回到原始 `back` 地址。

## 七、P1：角色与权限接入

P0 跑通后，再接 P1。P1 的目标是让登录用户在本系统里拥有正确角色和权限。

### 7.1 默认角色：`SsoClientRoleProvider`

如果所有 SSO 登录用户都应该自动获得某些基础角色，可以实现：

```java
public interface SsoClientRoleProvider {

    Collection<String> getDefaultRoleCodes();
}
```

示例：

```java
@Service
public class DemoSsoClientRoleProvider implements SsoClientRoleProvider {

    @Override
    public Collection<String> getDefaultRoleCodes() {
        return List.of("common_user");
    }
}
```

使用场景：

- SSO 登录用户默认拥有“普通用户”角色。
- 新注册同步过来的用户需要一个基础角色才能进入系统。

注意：

- 默认角色适合放“基础可用权限”。
- 不建议把高权限角色放入默认角色。

### 7.2 Client 超管识别：`SsoRoleBindingService`

SSO Server 可以维护某个用户是否是某个 Client 的超级管理员。Client 登录时，Starter 会查询并同步这个信息。

如果你的本地系统需要把“Client 超管”绑定到本地角色，实现：

```java
public interface SsoRoleBindingService {

    Collection<String> listRoleCodesByUserId(Long userId);

    void bindSuperAdminRole(Long userId);

    void unbindSuperAdminRole(Long userId);
}
```

典型逻辑：

| 方法 | 作用 |
| --- | --- |
| `listRoleCodesByUserId` | 查询本地用户当前角色 |
| `bindSuperAdminRole` | 给本地用户绑定超管角色 |
| `unbindSuperAdminRole` | 取消本地用户超管角色 |

登录链路中，Starter 会通过 SSO 消息查询用户是否是当前 Client 超管。消息类型来自 core：

```java
QUERY_USER_ROLES
```

如果 SSO Server 返回该用户是 Client 超管，Starter 会调用 `bindSuperAdminRole`；否则会调用 `unbindSuperAdminRole`。

### 7.3 业务代码判断是否超管

登录完成后，Starter 会把超管状态写入 TokenSession。业务代码可以通过工具方法判断：

```java
boolean superAdmin = SsoClientUtil.isSuperAdmin();
```

适合用于：

- 菜单过滤。
- 操作权限判断。
- 页面按钮权限判断。

### 7.4 本地变更同步回 SSO Server

如果你在 Client 本地修改了某个用户的超管状态，需要同步回 SSO Server，可以使用：

```java
SsoSyncHelper.syncSuperAdmin(centerId, true);
SsoSyncHelper.syncSuperAdmin(centerId, false);
```

注意这里的 `centerId` 是 **SSO Server 用户 ID**，不是本地用户 ID。通常你需要先通过本地映射表找到对应的 SSO 用户 ID。

## 八、P1：前端用户菜单

Web SDK 提供了可选的 `SsoUserMenu` 组件，用于快速接入统一风格的用户菜单。

菜单中的「账号与安全」「个人中心」不是普通外链。它们会先调用当前 Client 后端的 `/sso/getSsoPortalUrl`，由后端确认当前 Client 用户已登录、找到对应的 SSO 用户 ID，再向 SSO Server 申请一个 60 秒内有效、只能使用一次的 portal ticket。认证中心收到 `/portal-login?ticket=xxx&_back=/ucenter/applications` 后消费票据并写入认证中心登录态，最后进入目标页面。

这样设计的原因是：Client 站点和认证中心站点可能使用不同域名、IP 或端口，浏览器 Cookie 不能天然共享。**不要依赖 `127.0.0.1:9800` 与 `127.0.0.1:3310`、域名与 IP、或不同子域之间的 Cookie 共享来判断认证中心是否登录**。产品化接入应显式走 portal ticket，登录态由认证中心自己写入。

安全约束：

| 约束 | 当前实现 |
| --- | --- |
| 票据来源 | 只能由已登录的 Client 后端申请 |
| 用户身份 | 通过 `SsoUserMappingService.toServerUserId` 从本地用户映射到 SSO 用户 |
| Client 权限 | SSO Server 会检查该用户是否允许从当前 Client 进入认证中心 |
| 有效期 | portal ticket 默认 60 秒 |
| 使用次数 | ticket 被认证中心消费后立即失效 |
| 跳转范围 | 只允许 `/ucenter/applications`、`/ucenter/profile`、`/ucenter/password`、`/ucenter/login-log` |
基础用法：

```vue
<template>
  <SsoUserMenu
    :display-name="userInfo.nickname"
    :username="userInfo.username"
    @logout="handleLogout"
  />
</template>

<script setup lang="ts">
import { SsoUserMenu } from 'sz-sso-client-web-sdk/vue';
import 'sz-sso-client-web-sdk/style.css';

const handleLogout = async () => {
  await logoutApi();
  userStore.setToken('');
};
</script>
```

更多交互规范、插槽和样式建议见：

```text
docs/sso-user-menu-integration.md
```

如果你的系统已经有成熟的头像菜单，可以不使用该组件，只保留 SDK 的登录回调能力。

## 九、P2：扩展消息能力

当 P0、P1 稳定后，再考虑 P2。

SSO Server 与 Client 之间的扩展通讯基于 Sa-Token SSO message。当前已经落地的常见消息包括：

| 消息 | 方向 | 作用 |
| --- | --- | --- |
| `PUSH_REGISTER_USER` | Server -> Client | 推送 SSO 新注册用户到 Client |
| `QUERY_USER_ROLES` | Client -> Server | 查询用户在当前 Client 下的角色或超管信息 |
| `SYNC_USER_ROLES` | Client -> Server | 同步 Client 本地角色变化到 SSO Server |
| `CREATE_PORTAL_TICKET` | Client -> Server | 为已登录 Client 用户申请认证中心个人门户一次性入口 |

如果需要扩展新的消息，建议遵循：

1. 在 core 中定义清晰的消息类型和字段常量。
2. Server 侧实现对应 message handler。
3. Client 侧只做必要业务适配，不直接耦合 Server 数据库。
4. 先用 demo 验证消息参数和返回结构，再沉淀到 SDK 文档。

## 十、配置速查

### 10.1 后端配置

| 配置项 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `sa-token.sso-client.client` | 是 | `sso-client3` | Client 标识 |
| `sa-token.sso-client.server-url` | 是 | `http://127.0.0.1:5001/api/sso` | SSO Server 后端 API |
| `sa-token.sso-client.auth-url` | 是 | `http://127.0.0.1:3310/login` | 认证中心前端登录页 |
| `sa-token.sso-client.reg-logout-call` | 建议开启 | `true` | 是否注册单点登出回调 |
| `sa-token.sso-client.push-url` | 建议配置 | `http://127.0.0.1:5001/api/sso/pushS` | 当前 Client 提供给 Server 的推送地址 |

### 10.2 前端配置

| 配置项 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `clientFlag` | 是 | 无 | Client 标识，对齐后端 `client` |
| `ssoClientApiBaseUrl` | 是 | 无 | Client 后端 API 基础路径 |
| `authCenterBaseUrl` | 是 | 无 | 认证中心前端地址 |
| `callbackPath` | 否 | `/sso-login` | 登录回调路由 |
| `portalRoutes.login` | 否 | `/login` | 认证中心登录页路径 |
| `portalRoutes.security` | 否 | `/ucenter/password` | 认证中心账号与安全路径 |
| `portalRoutes.applications` | 否 | `/ucenter/applications` | 认证中心个人中心/我的应用路径 |
| `endpoints.loginByTicket` | 否 | `/sso/doLoginByTicket` | ticket 换 token 端点 |
| `endpoints.portalUrl` | 否 | `/sso/getSsoPortalUrl` | 获取认证中心个人门户一次性入口 |
| `successCode` | 否 | `0000` | 后端业务成功码 |
| `fetchCredentials` | 否 | `same-origin` | fetch credentials 配置 |
| `onLoginSuccess` | 是 | 无 | 换票成功后的业务回调 |

## 十一、推荐目录改动清单

如果你正在接入一个新的业务系统，通常会修改这些位置。

### 11.1 Java 后端

| 文件 | 动作 |
| --- | --- |
| `pom.xml` | 添加 `sz-sso-client-starter` 依赖 |
| `application.yml` 或 profile 配置 | 增加 `sa-token.sso-client` 配置 |
| 用户模块 Service | 增加 SSO 用户与本地用户映射查询或创建逻辑 |
| 新增实现类 | 实现 `SsoUserMappingService` |
| 新增实现类 | 实现 `SsoClientLoginAdapter` |
| 可选实现类 | 实现 `SsoClientRoleProvider` |
| 可选实现类 | 实现 `SsoRoleBindingService` |

### 11.2 Vue 前端

| 文件 | 动作 |
| --- | --- |
| `package.json` | 添加 `sz-sso-client-web-sdk` |
| `main.ts` | 创建 `createSsoClient` 并注册 `createSsoPlugin` |
| 静态路由文件 | 加入 `...getSsoRoutes()` |
| 路由守卫或白名单配置 | 加入 `SSO_CALLBACK_PATH`、`SSO_FORBIDDEN_PATH` |
| 登录页 | SSO 登录按钮跳 `/sso-login?back=xxx` |
| 退出逻辑 | 调 `/sso/logout` 后清理本地 store、路由、权限、连接 |
| 可选布局组件 | 接入 `SsoUserMenu` |

## 十二、常见问题

### 12.1 前端一直在登录页和 `/sso-login` 之间跳转

优先检查：

- `/sso-login` 是否加入路由白名单。
- `getSsoRoutes()` 是否注册到静态路由。
- 业务登录页是否正确传递 `back`。
- 认证中心回跳地址是否是业务前端地址，而不是后端地址。

### 12.2 `/sso/doLoginByTicket` 返回失败

优先检查：

- `ticket` 是否已经过期或被重复使用。
- 后端 `server-url` 是否正确。
- `client` 是否与 SSO Server 登记一致。
- `SsoUserMappingService.resolveOrProvisionClientUser` 是否返回本地用户 ID。
- 本地用户是否被禁用、删除或缺少必要状态。

### 12.3 前端保存 token 后仍然无权限

优先检查：

- `onLoginSuccess` 是否确实写入了业务系统原本使用的 token store。
- 后端返回字段是否叫 `accessToken`。
- 菜单、角色、按钮权限是否在登录后重新拉取。
- 动态路由是否在 SSO 登录成功后重新挂载。

### 12.4 退出后又自动登录回来

优先检查：

- 是否只清了本地 token，没有调用 `/sso/logout`。
- 是否没有清理菜单权限和动态路由。
- 是否存在未关闭的 WebSocket 或轮询把旧状态写回 store。
- 认证中心会话仍然有效时，业务侧是否设计为自动 SSO 登录。

### 12.5 `push-url` 应该填什么

`push-url` 是 SSO Server 调用当前 Client 的地址。demo 中填写：

```yaml
push-url: http://127.0.0.1:5001/api/sso/pushS
```

不要把它理解成 Starter 自动提供的 Sa-Token Client 消息入口。

`/sso/pushC` 是 Starter 自动端点，用于接收 Sa-Token Client 消息。上面的登记地址则是当前系统对外登记给 Server 使用的推送地址，实际项目里通常会带网关前缀、服务前缀或部署域名。

## 十三、最小可运行示例对照

### 13.1 Java demo 关键位置

| 位置 | 作用 |
| --- | --- |
| `config/local/sa-token.yml` | SSO Client 后端配置 |
| `sz-service/sz-service-admin/pom.xml` | Starter 依赖示例 |
| `SsoClientLoginAdapterImpl` | 登录用户构建与登录结果生成 |
| `SysUserMappingServiceImpl` | SSO 用户与本地用户映射 |
| `SsoClientRoleProviderImpl` | 默认角色示例 |
| `SsoRoleBindingServiceImpl` | Client 超管角色绑定示例 |

### 13.2 Web demo 关键位置

| 位置 | 作用 |
| --- | --- |
| `package.json` | 本地 Web SDK 依赖 |
| `src/main.ts` | `createSsoClient` 与插件注册 |
| `src/router/modules/staticRouter.ts` | `getSsoRoutes()` 接入 |
| `src/config/index.ts` | SSO 路由白名单 |
| `src/views/login/index.vue` | 业务登录页跳 `/sso-login` |
| `src/api/modules/system/login.ts` | `/sso/logout` 调用 |
| `.env.development` | 前端环境变量示例 |

## 十四、验证与构建建议

本轮文档只要求说明接入方式，不强制构建。但实际接入时建议按下面顺序验证：

### 14.1 后端

```bash
mvn compile -DskipTests
```

如果是多模块项目，建议只编译接入模块及其依赖：

```bash
mvn -pl <your-client-module> -am compile -DskipTests
```

### 14.2 前端

```bash
pnpm install
pnpm type-check
pnpm build
```

如果你只是本地联调，也可以先启动开发服务：

```bash
pnpm dev
```

## 十五、后续可讨论的 SDK 优化点

当前文档按现有代码能力编写。后续如果要继续优化 SDK，可以讨论这些方向：

1. 后端 Starter 增加更明确的启动期配置校验，提前提示缺少 `client`、`server-url`、`auth-url`。
2. 后端 Starter 对常见映射失败、ticket 失败、Client 不匹配提供更友好的错误码和排查信息。
3. Web SDK 增加更完整的 TypeScript 类型示例，让 `onLoginSuccess` 的返回结构更容易约束。
4. Web SDK 增加退出辅助函数，但仍保留业务方清理 store、路由、权限、WebSocket 的控制权。
5. 文档站后续可以将本文拆为“5 分钟快速开始”“Spring Boot 接入”“Vue 接入”“权限扩展”“故障排查”多个页面。

## 十六、接入结论

新业务系统接入 sz-sso 时，请先完成这 6 件事：

1. 后端引入 `sz-sso-client-starter`。
2. 后端配置 `sa-token.sso-client`。
3. 后端实现 `SsoUserMappingService`。
4. 后端实现 `SsoClientLoginAdapter`。
5. 前端注册 `createSsoClient`、`createSsoPlugin`、`getSsoRoutes()`。
6. 前端登录页跳 `/sso-login`，退出时调用 `/sso/logout` 并清理本地状态。

完成后，你就拥有了最小可用的统一登录能力；再按业务需要逐步补充默认角色、Client 超管、用户菜单和消息扩展。
