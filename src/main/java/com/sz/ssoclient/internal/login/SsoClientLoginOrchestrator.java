package com.sz.ssoclient.internal.login;

import com.sz.ssoclient.internal.messaging.SsoClientMessageGateway;
import com.sz.ssoclient.spi.SsoClientIdentityAdapter;
import com.sz.ssoclient.spi.SsoClientLoginAdapter;
import com.sz.ssoclient.spi.SsoClientLoginContext;
import com.sz.ssoclient.spi.SsoClientLoginResult;
import com.sz.ssoclient.spi.SsoClientSessionHandle;
import com.sz.ssoclient.spi.SsoDefaultAccessInitializer;
import com.sz.ssoclient.spi.SsoDefaultAccessStatus;
import com.sz.ssoclient.spi.SsoSuperAdminAuthorityAdapter;
import com.sz.ssoclient.spi.advanced.SsoClientLocalSessionAccessor;
import com.sz.ssocore.SsoMessageResult;
import com.sz.ssocore.SsoMessageTypes;
import com.sz.ssocore.SsoProtocolFields;
import com.sz.ssocore.signout.SsoLocalOutcome;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;

/** 不允许宿主重排安全步骤的固定 SSO Client 登录编排器。 */
@Slf4j
public class SsoClientLoginOrchestrator<U> {

    @FunctionalInterface
    public interface PostSessionAction {
        void run(SsoClientLoginResult result);
    }

    private final SsoTicketAuthenticator ticketAuthenticator;
    private final SsoClientIdentityResolver identityResolver;
    private final SsoClientMessageGateway messageGateway;
    private final SsoClientIdentityAdapter identityAdapter;
    private final SsoClientLoginAdapter<U> loginAdapter;
    private final SsoDefaultAccessInitializer defaultAccessInitializer;
    private final SsoSuperAdminAuthorityAdapter superAdminAuthorityAdapter;
    private final SsoClientLocalSessionAccessor localSessionAccessor;

    public SsoClientLoginOrchestrator(
            SsoTicketAuthenticator ticketAuthenticator,
            SsoClientIdentityResolver identityResolver,
            SsoClientMessageGateway messageGateway,
            SsoClientIdentityAdapter identityAdapter,
            SsoClientLoginAdapter<U> loginAdapter,
            SsoDefaultAccessInitializer defaultAccessInitializer,
            SsoSuperAdminAuthorityAdapter superAdminAuthorityAdapter,
            SsoClientLocalSessionAccessor localSessionAccessor) {
        this.ticketAuthenticator = Objects.requireNonNull(ticketAuthenticator, "ticketAuthenticator");
        this.identityResolver = Objects.requireNonNull(identityResolver, "identityResolver");
        this.messageGateway = Objects.requireNonNull(messageGateway, "messageGateway");
        this.identityAdapter = Objects.requireNonNull(identityAdapter, "identityAdapter");
        this.loginAdapter = Objects.requireNonNull(loginAdapter, "loginAdapter");
        this.defaultAccessInitializer = defaultAccessInitializer;
        this.superAdminAuthorityAdapter = superAdminAuthorityAdapter;
        this.localSessionAccessor = Objects.requireNonNull(localSessionAccessor, "localSessionAccessor");
    }

    public SsoClientLoginResult login(String ticket) {
        return login(ticket, ignored -> { });
    }

    public SsoClientLoginResult login(
            String ticket,
            PostSessionAction postSessionAction) {
        Objects.requireNonNull(postSessionAction, "postSessionAction");

        SsoTicketAuthenticator.AuthenticatedTicket authenticated =
                ticketAuthenticator.authenticate(ticket);
        long ssoUserId = authenticated.ssoUserId();
        String localUserId = identityResolver.resolve(ssoUserId);
        boolean superAdmin = querySuperAdmin(ssoUserId);
        boolean superAdminApplied = false;
        if (superAdmin) {
            if (superAdminAuthorityAdapter == null) {
                throw new IllegalStateException("Client 未接入 SSO 超管本地权限能力");
            }
            superAdminAuthorityAdapter.apply(localUserId, true);
            superAdminApplied = true;
        }

        initializeDefaultAccess(localUserId, superAdminApplied);
        U user = loginAdapter.loadLoginUser(localUserId);
        if (user == null) {
            throw new IllegalStateException("Client 登录 Adapter 未返回本地登录用户");
        }
        SsoClientLoginResult result = loginAdapter.establishSession(
                user,
                new SsoClientLoginContext(
                        ssoUserId,
                        localUserId,
                        authenticated.deviceId(),
                        superAdmin));
        if (result == null) {
            throw new IllegalStateException("Client 登录 Adapter 未返回 Session 结果");
        }
        try {
            postSessionAction.run(result);
            return result;
        } catch (RuntimeException originalFailure) {
            compensateExact(result.sessionHandle(), originalFailure);
            throw originalFailure;
        }
    }

    private boolean querySuperAdmin(long ssoUserId) {
        SsoMessageResult<Boolean> result = messageGateway.send(
                SsoMessageTypes.QUERY_CLIENT_SUPER_ADMIN_STATUS,
                Map.of(SsoProtocolFields.SSO_USER_ID, ssoUserId),
                Boolean.class);
        if (result == null || !result.successful() || result.data() == null) {
            throw new IllegalStateException(result == null || result.message() == null
                    ? "SSO 超管状态查询失败"
                    : result.message());
        }
        return result.data();
    }

    private void initializeDefaultAccess(String localUserId, boolean superAdminApplied) {
        try {
            SsoDefaultAccessStatus status;
            if (defaultAccessInitializer == null) {
                identityAdapter.completeDefaultAccessInitialization(localUserId);
                status = SsoDefaultAccessStatus.DISABLED;
            } else {
                status = defaultAccessInitializer.initialize(localUserId);
                if (status == null) {
                    throw new IllegalStateException("默认访问 Initializer 返回了 null");
                }
            }
            log.debug("[SSO] Client 默认访问处理完成, localUserId={}, status={}", localUserId, status);
        } catch (RuntimeException exception) {
            if (!superAdminApplied) {
                throw exception;
            }
            log.warn("[SSO] 已应用超管的用户默认访问初始化失败；本次允许登录且完成标记保持未完成, localUserId={}",
                    localUserId, exception);
        }
    }

    private void compensateExact(SsoClientSessionHandle handle, RuntimeException originalFailure) {
        try {
            SsoLocalOutcome outcome = localSessionAccessor.revokeExact(handle);
            if (outcome == SsoLocalOutcome.FAILED) {
                log.error("[SSO] 登录后续步骤失败，精确 Session 补偿也失败, sessionHandle={}, original={}",
                        handle.value(), originalFailure.getMessage());
            }
        } catch (RuntimeException compensationFailure) {
            log.error("[SSO] 登录后续步骤失败，精确 Session 补偿抛出异常, sessionHandle={}, original={}",
                    handle.value(), originalFailure.getMessage(), compensationFailure);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
    }
}
