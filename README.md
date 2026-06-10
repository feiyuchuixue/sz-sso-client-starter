# sz-sso-client-starter

## 背景

`sz-sso-client-starter` 是 `sz-sso` 开源版配套的 Client 端 Spring Boot Starter，基于 [Sa-Token SSO 模式三](https://sa-token.cc/doc.html#/sso/sso-type3) 封装。

业务系统接入它之后，不需要自己处理 ticket 校验、SSO Client 标准端点、单点注销回调、Server 消息推送、注册同步、平台超管状态同步等重复工作。你只需要实现几个和本地用户体系相关的 SPI，Starter 就会把 SSO 登录结果转换成本系统自己的登录态。

当前开源版聚焦账号密码统一登录、Ticket 协议、注册同步、Client 接入、Client 超管同步与基础管理能力。第三方登录、OAuth Provider、OIDC、SAML、LDAP、MFA 等能力不属于当前开源版 Starter 范围。

---

## 一分钟理解

一个业务系统接入 SSO 后，最容易卡在三件事上：

1. **SSO Server 的用户 ID 怎么变成本系统的用户 ID？**
2. **ticket 校验成功后，怎么创建本系统自己的登录态？**
3. **平台指定的 Client 超管，怎么同步成本系统里的角色或权限？**

Starter 做的是协议层和流程层的事：

```text
SSO Server
    |
    | ticket / message
    v
sz-sso-client-starter
    |
    | SPI
    v
业务系统自己的用户、角色、权限、登录态
```

它不会越过业务系统直接写你的用户库、角色库、权限库。所有本地业务决策都通过 SPI 交给业务系统自己实现。

---

## 它解决什么问题

### 1. 第一次登录没有角色怎么办？

新用户第一次通过 SSO 登录某个 Client 时，本地可能还没有角色记录。Starter 可以在登录流程中调用 `SsoRoleBindingService.applyDefaultRole()`，让业务系统给新用户写入默认角色。

这个步骤是可选的。不需要默认角色能力时，不实现 `SsoClientRoleProvider` 即可。

### 2. 第一个超管从哪里来？

平台管理员可以在 SSO Server 端指定某个用户是某个 Client 的超管。用户登录 Client 时，Starter 会向 Server 查询：

```text
QUERY_USER_ROLES -> isSuperAdmin: true / false
```

然后通过 `SsoRoleBindingService.applySuperAdmin()` 把这个状态同步到本地。业务系统可以把它落到 `user_tag`、角色表、权限表，或者自己的管理员标记中。

### 3. Client 本地改了超管，Server 怎么知道？

如果业务系统内部修改了用户的超管身份，可以调用 `SsoSyncHelper.syncSuperAdmin()` 异步通知 Server，同步更新平台侧的 Client 超管名单。

```text
Client 本地变更 -> SsoSyncHelper -> SYNC_SUPER_ADMIN -> SSO Server
```

---

## 架构总览

```text
┌──────────────────────────────────────────────────────────┐
│                      SSO Server                          │
│                                                          │
│  - 统一登录 / 注销 / Ticket                              │
│  - 维护 SSO 用户与 Client 管理关系                       │
│  - 处理 QUERY_USER_ROLES、SYNC_SUPER_ADMIN 等消息         │
└──────────────┬────────────────────────┬──────────────────┘
               │                        ▲
     登录时查询超管状态            Client 主动回传变更
               │                        │
               ▼                        │
┌──────────────▼────────────────────────┴──────────────────┐
│                sz-sso-client-starter                     │
│                                                          │
│  - 自动注册 /sso/** Client 端点                           │
│  - 编排 ticket 登录流程                                   │
│  - 发送和接收 Sa-Token SSO message                       │
│  - 将超管状态写入 TokenSession                           │
│  - 通过 SPI 对接业务系统                                  │
└──────────────────────────┬───────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────┐
│                   业务 Client 系统                        │
│                                                          │
│  - 实现用户映射、登录适配、角色绑定等 SPI                  │
│  - 维护本地用户、角色、菜单、数据权限                      │
│  - 创建本系统自己的登录态                                  │
└──────────────────────────────────────────────────────────┘
```

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

Starter 已依赖 `sz-sso-core`、`sa-token-sso`、`sa-token-redis-template`、`sa-token-forest`、`spring-boot-starter-web` 和 Spring Boot 自动配置能力。

### 第二步：配置 `application.yml`

```yaml
sa-token:
  token-name: Authorization
  timeout: 604800
  active-timeout: 86400
  is-concurrent: true
  is-share: false
  token-style: uuid
  is-read-header: true
  is-read-cookie: false
  token-prefix: Bearer

  sso-client:
    mode: sso-client3
    client: your-client-flag
    server-url: http://sso-server-host/api/admin
    auth-url: http://sso-server-host/login
    is-http: true
    is-slo: true
    reg-logout-url: true
    secret-key: your-secret-key
    push-url: http://your-client-host/sso/pushC
```

几个最容易配错的项：

| 配置项 | 说明 |
| --- | --- |
| `client` | 当前 Client 标识，必须先在 SSO Server 端登记 |
| `server-url` | SSO Server 后端 API 地址 |
| `auth-url` | 认证中心前端登录页地址 |
| `secret-key` | Client 与 Server 共享的签名密钥，生产环境必须更换 |
| `push-url` | 当前 Client 暴露给 Server 的消息推送地址，一般是 `http://host/sso/pushC` |

### 第三步：实现 2 个必须 SPI

#### `SsoUserMappingService`：用户 ID 映射

SSO Server 有自己的用户 ID，业务系统也有自己的用户 ID。这个接口负责双向转换，并处理 Server 推来的注册同步消息。

```java
import cn.dev33.satoken.sso.message.SaSsoMessage;
import com.sz.ssoclient.spi.SsoUserMappingService;
import com.sz.ssocore.SsoUserMeta;
import com.sz.ssocore.SsoUserMetaUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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

#### `SsoClientLoginAdapter<U>`：构建用户并创建登录态

这个接口决定 ticket 登录成功后，业务系统最终怎么登录。

```java
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.sz.core.common.entity.LoginUser;
import com.sz.security.core.util.LoginUtils;
import com.sz.ssoclient.pojo.SsoLoginResult;
import com.sz.ssoclient.spi.SsoClientLoginAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SsoClientLoginAdapterImpl implements SsoClientLoginAdapter<LoginUser> {

    private final SysUserService userService;

    @Override
    public LoginUser buildLoginUser(Long userId) {
        return userService.buildLoginUser(userId);
    }

    @Override
    public SsoLoginResult createLoginResult(LoginUser loginUser, SaLoginParameter parameter, Object loginId) {
        LoginUtils.performLogin(loginUser, parameter, null);
        return SsoLoginResult.of(StpUtil.getTokenValue(), StpUtil.getTokenTimeout(), loginUser);
    }
}
```

只实现这两个 SPI，Starter 的基础登录链路就能启动。

### 第四步：按需实现角色相关 SPI

#### `SsoClientRoleProvider`：提供默认角色 key

```java
import com.sz.ssoclient.spi.SsoClientRoleProvider;
import org.springframework.stereotype.Component;

@Component
public class SsoClientRoleProviderImpl implements SsoClientRoleProvider {

    @Override
    public String getDefaultRoleKey() {
        return "dict_menu";
    }
}
```

不实现时，首次登录默认角色初始化会跳过。

#### `SsoRoleBindingService`：执行本地角色和超管同步

```java
import com.sz.ssoclient.spi.SsoRoleBindingService;
import org.springframework.stereotype.Service;

@Service
public class SsoRoleBindingServiceImpl implements SsoRoleBindingService {

    @Override
    public void applyDefaultRole(Long localUserId, String defaultRoleKey) {
        // 用户没有任何角色时，按 defaultRoleKey 写入本地默认角色。
    }

    @Override
    public void applySuperAdmin(Long localUserId, boolean isSuperAdmin) {
        // 将平台认定的 Client 超管状态同步到本地用户标记、角色或权限表。
    }
}
```

不实现时，Starter 会注册 `com.sz.ssoclient.sync.DefaultSsoRoleBindingService`，只打印日志，不写业务库。

---

## 登录流程

Starter 的登录流程是“先写本地权限状态，再构建登录用户”。这样 `buildLoginUser()` 从数据库读取用户信息时，已经能读到前面同步好的默认角色和超管状态。

```text
ticket 校验通过
        |
        v
PrepareLoginParameterStep
解析 loginId、deviceId、timeout
        |
        v
ApplyDefaultRoleStep
可选：首次登录默认角色初始化
        |
        v
QuerySuperAdminStep
向 Server 查询当前用户是否为本 Client 超管
        |
        v
ApplySuperAdminStep
可选：把超管状态同步到本地权限体系
        |
        v
BuildLoginUserStep
从本地 DB 构建完整 LoginUser
        |
        v
CreateLoginResultStep
创建本地登录态，返回 SsoLoginResult
        |
        v
WriteTokenSessionStep
写入 TokenSession: isSuperAdmin
```

登录时任何可选步骤异常都会降级并记录 warn 日志，不会中断 ticket 登录。超管查询失败时，默认按非超管处理。

---

## 自动注册的端点

Starter 会自动注册 `/sso` 前缀下的 Client 端点，已标注 `@SaIgnore`：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/sso/isLogin` | 查询当前登录状态 |
| `GET` | `/sso/getSsoAuthUrl` | 生成认证中心跳转地址 |
| `GET` | `/sso/doLoginByTicket` | ticket 校验并完成本地登录 |
| `ANY` | `/sso/logout` | 发起单点注销 |
| `ANY` | `/sso/logoutCall` | 接收 Server 单点注销回调 |
| `ANY` | `/sso/pushC` | 接收 Server 消息推送 |

接口响应使用 `com.sz.ssocore.dto.SsoApiResult`，JSON 结构为：

```json
{ "code": "0000", "message": "SUCCESS", "data": {}, "param": {} }
```

---

## Client 主动同步超管变更

当业务系统本地变更某个用户的超管身份后，可以调用 `SsoSyncHelper` 通知 Server。

```java
import com.sz.ssoclient.sync.SsoSyncHelper;
import org.springframework.beans.factory.annotation.Autowired;

@Autowired(required = false)
private SsoSyncHelper ssoSyncHelper;

public void changeUserTag(Long localUserId, boolean isSuperAdmin) {
    // 1. 先完成本地 DB 更新。

    // 2. 再通知 Server。非 SSO 环境下此 Bean 可能不存在。
    if (ssoSyncHelper != null) {
        ssoSyncHelper.syncSuperAdmin(localUserId, isSuperAdmin);
    }
}
```

`syncSuperAdmin()` 是异步方法，失败只记录 warn 日志，不回滚本地业务操作。

---

## 常用工具与常量

### `SsoClientUtil`

```java
import com.sz.ssoclient.SsoClientUtil;

boolean isSuperAdmin = SsoClientUtil.isSuperAdmin();
```

这个值来自当前 TokenSession 中的 `isSuperAdmin`。未登录、TokenSession 不存在或查询失败时返回 `false`。

### core 常量

协议常量统一由 `sz-sso-core` 提供：

```java
import com.sz.ssocore.SsoMessageTypes;
import com.sz.ssocore.SsoProtocolFields;

SsoMessageTypes.REGISTER
SsoMessageTypes.USER_CHECK
SsoMessageTypes.QUERY_USER_ROLES
SsoMessageTypes.SYNC_SUPER_ADMIN
SsoMessageTypes.SYNC_CLIENT_SUPER_ADMIN

SsoProtocolFields.IS_SUPER_ADMIN // "isSuperAdmin"
```

### 用户元信息

```java
import com.sz.ssocore.SsoUserMeta;
import com.sz.ssocore.SsoUserMetaUtils;

SsoUserMeta meta = SsoUserMetaUtils.fromEntries(message.getDataMap().entrySet());
```

`SsoUserMeta` 包含：`ssoUserId`、`username`、`nickname`、`email`、`phone`、`avatarUrl`、`createTime`。

---

## 自动装配条件

Starter 只有在业务系统已经提供必须 SPI 时才激活核心登录能力：

| 条件 | 说明 |
| --- | --- |
| classpath 存在 `SaSsoClientTemplate` | 已引入 Sa-Token SSO |
| 存在 `SsoUserMappingService` Bean | 业务方提供用户映射 |
| 存在 `SsoClientLoginAdapter` Bean | 业务方提供登录适配 |

缺少必须 SPI 时，启动阶段会给出更明确的失败提示，避免运行时才发现登录链路不可用。

---

## 包结构说明

| 包 | 职责 |
| --- | --- |
| `com.sz.ssoclient` | 根包，目前只保留 `SsoClientUtil` |
| `com.sz.ssoclient.spi` | 业务系统实现的扩展点 |
| `com.sz.ssoclient.sync` | Client 主动同步工具和默认角色绑定实现 |
| `com.sz.ssoclient.message` | 消息发送、拦截、分发和处理器接口 |
| `com.sz.ssoclient.message.handler` | Starter 内置消息处理器 |
| `com.sz.ssoclient.login` | 登录上下文、步骤接口和编排器 |
| `com.sz.ssoclient.login.step` | 默认登录步骤实现 |
| `com.sz.ssoclient.autoconfigure` | Spring Boot 自动配置 |
| `com.sz.ssoclient.pojo` | Starter 专属 DTO，如 `SsoLoginResult`、`LoginStatus` |

更完整的设计说明见：[docs/starter-refactor-design.md](docs/starter-refactor-design.md)。

---

## 从旧包名迁移

本次重构已删除 starter 根包下的兼容代理类。如果旧项目使用过早期 import，请按下表迁移：

| 旧导入 | 新导入 |
| --- | --- |
| `com.sz.ssoclient.SsoClientLoginAdapter` | `com.sz.ssoclient.spi.SsoClientLoginAdapter` |
| `com.sz.ssoclient.SsoUserMappingService` | `com.sz.ssoclient.spi.SsoUserMappingService` |
| `com.sz.ssoclient.SsoClientRoleProvider` | `com.sz.ssoclient.spi.SsoClientRoleProvider` |
| `com.sz.ssoclient.SsoRoleBindingService` | `com.sz.ssoclient.spi.SsoRoleBindingService` |
| `com.sz.ssoclient.SsoMessageSender` | `com.sz.ssoclient.message.SsoMessageSender` |
| `com.sz.ssoclient.SsoServerMessageHandler` | `com.sz.ssoclient.message.SsoServerMessageHandler` |
| `com.sz.ssoclient.SsoSyncHelper` | `com.sz.ssoclient.sync.SsoSyncHelper` |
| `com.sz.ssoclient.DefaultSsoRoleBindingService` | `com.sz.ssoclient.sync.DefaultSsoRoleBindingService` |
| `com.sz.ssoclient.SsoClientSuperAdminSyncHandler` | `com.sz.ssoclient.message.handler.SsoClientSuperAdminSyncHandler` |
| `com.sz.ssoclient.SsoUserMeta` | `com.sz.ssocore.SsoUserMeta` |
| `com.sz.ssoclient.SsoUserMetaUtils` | `com.sz.ssocore.SsoUserMetaUtils` |
| `com.sz.ssoclient.SsoCoreConstant` | `com.sz.ssocore.SsoMessageTypes` / `com.sz.ssocore.SsoProtocolFields` |
| `com.sz.ssoclient.pojo.SsoApiResult` | `com.sz.ssocore.dto.SsoApiResult` |

---

## 本地验证

```powershell
# 安装 core
E:\opt\apache-maven-3.9.6-bin\apache-maven-3.9.6\bin\mvn.cmd -f E:\dev\Code\Github\sso\sz-sso-core\pom.xml clean install

# 编译并测试 starter
E:\opt\apache-maven-3.9.6-bin\apache-maven-3.9.6\bin\mvn.cmd -f E:\dev\Code\Github\sso\sz-sso-client-starter\pom.xml clean install

# 联动编译 sso-server
E:\opt\apache-maven-3.9.6-bin\apache-maven-3.9.6\bin\mvn.cmd -f E:\dev\Code\Github\szdev\sz-sso-server\pom.xml clean compile -DskipTests
```

## 版本要求

| 依赖 | 版本 |
| --- | --- |
| Java | 21+ |
| Spring Boot | 3.x |
| Sa-Token | 1.45.0 |