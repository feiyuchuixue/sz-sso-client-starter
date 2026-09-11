# sz-sso-client-starter

`sz-sso-client-starter` 是普通 Spring Boot Client 接入 SSO 的唯一 Java Starter。它负责自动配置、Ticket 回调编排、消息注册、六条 Browser BFF 路由和安全基础设施；宿主仍负责本地用户、权限、Session 与 Socket 生命周期。

当前版本：`1.0.0-SNAPSHOT`。当前状态：验证中（公共发布 DEFERRED）。

## Maven 坐标

```xml
<dependency>
  <groupId>com.sz-dev</groupId>
  <artifactId>sz-sso-client-starter</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

当前只支持本地 SNAPSHOT 验证。先构建 `sz-sso-core`，再在本仓执行：

```shell
mvn clean install
```

公共仓发布仍为 DEFERRED。

## 唯一自动配置入口

Starter 只注册一个顶层入口：

```text
com.sz.ssoclient.autoconfigure.SsoClientAutoConfiguration
```

内部配置按 Core、SPI、基础设施、消息与 Web 责任拆分，但不形成第二套公共自动配置入口。`sa-token.sso-client` 由 Starter 绑定到当前 Spring Context，并同步到 Sa-Token 当前模板；宿主不需要自行创建兼容配置 Bean。

## 六个业务 SPI

| SPI | 必需性 | 宿主责任 |
| --- | --- | --- |
| `SsoClientLoginAdapter<U>` | 必需 | 按不透明本地用户 ID 加载用户，并建立本地 Session |
| `SsoClientIdentityAdapter` | 必需 | 查询 mapping、幂等 JIT 匹配/绑定/开户，并完成一次性默认访问标记 |
| `SsoClientIdentityPreparationService` | 可选 | 对显式选中用户做只读 readiness 与幂等 preparation |
| `SsoDefaultAccessInitializer` | 可选 | 首次访问当前 Client 时幂等初始化默认访问 |
| `SsoSuperAdminAuthorityAdapter` | 可选 | 幂等授予或撤销当前 Client 的 SSO 超管权限 |
| `SsoSuperAdminSnapshotProvider` | 可选 | 返回当前 Client 的完整超管不透明本地 ID 快照 |

JIT 只发生在用户首次访问当前 Client 时。它不会因为中心注册或其他 Client 登录而广播创建全部 Client 账户。

## 三个高级 SPI

| SPI | 使用场景 |
| --- | --- |
| `SsoClientLocalSessionAccessor` | 读取可信本地会话，并按精确句柄、设备或账号撤销 |
| `SsoClientMessageHandler` | 注册唯一 `CUSTOM_*` 自定义消息处理器 |
| `SsoClientStateRepository` | 为登录事务和幂等状态提供带 TTL 的原子存储；多实例时必须共享 |

高级 SPI 用于替换基础设施或扩展自定义消息，不应复制 Starter 内部 Handler。

## 两个调用 API

| API | 责任 |
| --- | --- |
| `SsoClientMessageSender` | 发送 Core 允许的自定义消息并返回中立结果 |
| `SsoClientSuperAdminSyncOperations` | 把本地 SSO 超管变化同步到 Server，原样保留失败 |

普通业务代码不得直接依赖 Starter 内部 transport、router 或 first-party 类型。

## 六条 Browser BFF 路由

浏览器只调用当前 Client 后端。固定合同全部为 `POST`：

| 路由 | 作用 |
| --- | --- |
| `/sso/v1/login/transactions` | 创建绑定当前 Browser 的登录事务 |
| `/sso/v1/login/callback` | 单次消费 Ticket 与 State，建立业务系统本地登录态 |
| `/sso/v1/session/logout` | 只退出当前 Client 本地会话 |
| `/sso/v1/signouts/device` | 退出同账号当前设备上的已接入业务应用 |
| `/sso/v1/signouts/account` | 退出同账号所有设备上的已接入业务应用 |
| `/sso/v1/portal/entries` | 为语义化 Ucenter 目标创建 Portal 入口 |

响应固定为 `code/message/data`，成功码是 `0000`。Ticket、State、Browser 绑定、固定 callback 和安全 back 任一校验失败都必须 fail-close；Ticket 第一次消费尝试后不可重用。

## 最小配置

Secret 只通过环境变量注入，不写入仓库、日志或文档示例值。

```yaml
sz:
  sso-client:
    enabled: true
    external-origin: ${SSO_CLIENT_ORIGIN}
    allow-missing-browser-source: false
    secure-cookie: true

sa-token:
  sso-client:
    mode: sso-client
    client: ${SSO_CLIENT_ID}
    server-url: ${SSO_SERVER_URL}
    auth-url: ${SSO_AUTH_URL}
    push-url: ${SSO_SERVER_PUSH_URL}
    curr-sso-login: ${SSO_CLIENT_CALLBACK_URL}
    secret-key: ${SSO_CLIENT_SECRET}
```

`curr-sso-login` 必须是当前 Client 前端固定 `/sso-login` 页面对应的绝对 URL，不是后端 BFF。完整 URL 方向、生产 Cookie/CORS 与反向代理配置由中文文档站统一说明。

## 宿主登录生命周期

回调成功不等于宿主状态已经安全替换。宿主应按固定顺序处理：

1. 清理旧 Token、用户、菜单、动态路由、标签页缓存和 Socket。
2. Starter 调用 `SsoClientLoginAdapter` 建立新的本地 Session。
3. Browser 保存新 Token，拉取用户与权限，重建动态路由。
4. 最后恢复 Socket。

任何一步失败都必须清理局部状态并返回稳定失败，不能回退到旧账号或显示假成功。

## 本地验证

```shell
mvn clean test
mvn clean install
mvn -f verification/boot-3.5.5-consumer/pom.xml clean verify
mvn -f verification/boot-4.0.6-consumer/pom.xml clean verify
```

已实现的二进制 fixture 精确覆盖 Spring Boot `3.5.5` 与 `4.0.6`。其他组合只有进入兼容矩阵并完成真实验证后才会公开记录。

完整接入、Web SDK、Portal、退出、升级和排障请阅读：[Starter 接入](https://sz-dev.com/integration/sdk-starter)。
