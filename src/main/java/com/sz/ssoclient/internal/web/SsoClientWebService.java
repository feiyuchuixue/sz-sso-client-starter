package com.sz.ssoclient.internal.web;

import cn.dev33.satoken.sso.exception.SaSsoException;
import com.sz.ssoclient.api.browser.SsoLocalLogoutResponse;
import com.sz.ssoclient.api.browser.SsoLoginCallbackRequest;
import com.sz.ssoclient.api.browser.SsoLoginCallbackResponse;
import com.sz.ssoclient.api.browser.SsoLoginTransactionRequest;
import com.sz.ssoclient.api.browser.SsoLoginTransactionResponse;
import com.sz.ssoclient.api.browser.SsoPortalEntryRequest;
import com.sz.ssoclient.api.browser.SsoPortalEntryResponse;
import com.sz.ssoclient.api.browser.SsoPortalTarget;
import com.sz.ssoclient.api.browser.SsoSignoutResponse;
import com.sz.ssoclient.api.browser.SsoWebCodes;
import com.sz.ssoclient.internal.login.SsoClientLoginOrchestrator;
import com.sz.ssoclient.internal.login.transaction.SsoLoginTransactionService;
import com.sz.ssoclient.internal.messaging.SsoClientMessageGateway;
import com.sz.ssoclient.spi.SsoClientIdentityAdapter;
import com.sz.ssoclient.spi.SsoClientLoginResult;
import com.sz.ssoclient.spi.advanced.SsoClientLocalSession;
import com.sz.ssoclient.spi.advanced.SsoClientLocalSessionAccessor;
import com.sz.ssocore.SsoMessageResult;
import com.sz.ssocore.SsoMessageTypes;
import com.sz.ssocore.SsoProtocolFields;
import com.sz.ssocore.signout.SsoLocalOutcome;
import com.sz.ssocore.signout.SsoPropagationOutcome;
import com.sz.ssocore.signout.SsoPropagationReason;
import com.sz.ssocore.signout.SsoSignoutReceipt;
import com.sz.ssocore.signout.SsoSignoutScope;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/** 六条 Browser 路由的固定业务编排。 */
@Slf4j
public class SsoClientWebService {

    private static final int MAX_BACK_LENGTH = 2048;
    private static final int MAX_TICKET_LENGTH = 512;
    private static final Pattern STATE = Pattern.compile("[A-Za-z0-9_-]{22,128}");

    @FunctionalInterface
    public interface LoginAuthorizationUrlFactory {
        String create(String state, String theme);
    }

    @FunctionalInterface
    public interface PortalUrlFactory {
        String create(String ticket, SsoPortalTarget target, String targetPath);
    }

    private final SsoLoginTransactionService transactions;
    private final SsoClientLoginOrchestrator<?> loginOrchestrator;
    private final SsoClientMessageGateway messageGateway;
    private final SsoClientIdentityAdapter identityAdapter;
    private final SsoClientLocalSessionAccessor localSessions;
    private final LoginAuthorizationUrlFactory loginAuthorizationUrls;
    private final PortalUrlFactory portalUrls;

    public SsoClientWebService(
            SsoLoginTransactionService transactions,
            SsoClientLoginOrchestrator<?> loginOrchestrator,
            SsoClientMessageGateway messageGateway,
            SsoClientIdentityAdapter identityAdapter,
            SsoClientLocalSessionAccessor localSessions,
            LoginAuthorizationUrlFactory loginAuthorizationUrls,
            PortalUrlFactory portalUrls) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.loginOrchestrator = Objects.requireNonNull(loginOrchestrator, "loginOrchestrator");
        this.messageGateway = Objects.requireNonNull(messageGateway, "messageGateway");
        this.identityAdapter = Objects.requireNonNull(identityAdapter, "identityAdapter");
        this.localSessions = Objects.requireNonNull(localSessions, "localSessions");
        this.loginAuthorizationUrls = Objects.requireNonNull(
                loginAuthorizationUrls, "loginAuthorizationUrls");
        this.portalUrls = Objects.requireNonNull(portalUrls, "portalUrls");
    }

    public SsoLoginTransactionResponse createLoginTransaction(
            String browserId, SsoLoginTransactionRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.back() != null && request.back().length() > MAX_BACK_LENGTH) {
            throw redirectInvalid();
        }
        String theme = validateTheme(request.theme());
        SsoLoginTransactionService.Created created;
        try {
            created = transactions.create(browserId, request.back());
        } catch (IllegalArgumentException exception) {
            throw redirectInvalid();
        }
        String authorizationUrl = loginAuthorizationUrls.create(created.state(), theme);
        if (authorizationUrl == null || authorizationUrl.isBlank()) {
            throw serverFailure();
        }
        return new SsoLoginTransactionResponse(authorizationUrl);
    }

    public SsoLoginCallbackResponse completeLogin(
            String browserId, SsoLoginCallbackRequest request) {
        Objects.requireNonNull(request, "request");
        validateCallback(request);
        AtomicBoolean exchangeStarted = new AtomicBoolean(false);
        AtomicReference<SsoClientLoginResult> established = new AtomicReference<>();
        try {
            return transactions.consume(browserId, request.state(), transaction -> {
                exchangeStarted.set(true);
                SsoClientLoginResult login = loginOrchestrator.login(
                        request.ticket());
                established.set(login);
                return new SsoLoginCallbackResponse(login.accessToken(), transaction.back());
            });
        } catch (SsoClientWebException exception) {
            compensateIfEstablished(established.get());
            throw exception;
        } catch (RuntimeException exception) {
            compensateIfEstablished(established.get());
            log.warn("[SSO] Browser 登录回调失败, type={}, at={}",
                    exception.getClass().getName(), firstStackFrame(exception));
            if (!exchangeStarted.get()) {
                throw new SsoClientWebException(
                        SsoWebCodes.LOGIN_TRANSACTION_INVALID,
                        400,
                        "登录事务不存在、已过期、冲突或已消费");
            }
            if (exception instanceof SaSsoException) {
                throw new SsoClientWebException(
                        SsoWebCodes.LOGIN_TICKET_INVALID,
                        400,
                        "登录 Ticket 非法或已消费");
            }

            throw serverFailure();
        }
    }

    public SsoLocalLogoutResponse logoutLocalSession() {
        SsoClientLocalSession session;
        try {
            session = localSessions.current();
        } catch (RuntimeException exception) {
            log.warn("[SSO] 读取当前 Client Session 失败，无法确认本地退出结果");
            return new SsoLocalLogoutResponse(SsoLocalOutcome.FAILED);
        }
        if (session == null || !session.authenticated()) {
            return new SsoLocalLogoutResponse(SsoLocalOutcome.ALREADY_REVOKED);
        }
        return new SsoLocalLogoutResponse(revokeExact(session));
    }

    public SsoSignoutResponse signoutCurrentDevice() {
        return signout(SsoSignoutScope.CURRENT_DEVICE);
    }

    public SsoSignoutResponse signoutAccount() {
        return signout(SsoSignoutScope.ACCOUNT_GLOBAL);
    }

    public SsoPortalEntryResponse createPortalEntry(SsoPortalEntryRequest request) {
        Objects.requireNonNull(request, "request");
        SsoPortalTarget target = request.target();
        if (target == null) {
            throw new SsoClientWebException(
                    SsoWebCodes.PORTAL_TARGET_INVALID,
                    400,
                    "Portal 目标未注册或不允许");
        }
        SsoClientLocalSession session = requiredSession();
        Optional<Long> mapping;
        try {
            mapping = identityAdapter.findSsoUserId(session.localUserId());
        } catch (RuntimeException exception) {
            throw serverFailure();
        }
        if (mapping == null || mapping.isEmpty()) {
            throw new SsoClientWebException(
                    SsoWebCodes.CLIENT_FORBIDDEN,
                    403,
                    "当前用户不存在可信 SSO 映射");
        }
        SsoMessageResult<String> result = messageGateway.send(
                SsoMessageTypes.CREATE_PORTAL_TICKET,
                Map.of(
                        SsoProtocolFields.SSO_USER_ID, mapping.get(),
                        "targetPath", target.targetPath()),
                String.class);
        if (result == null || !result.successful()
                || result.data() == null || result.data().isBlank()) {
            throw serverFailure();
        }
        String portalUrl = portalUrls.create(result.data(), target, target.targetPath());
        if (portalUrl == null || portalUrl.isBlank()) {
            throw serverFailure();
        }
        return new SsoPortalEntryResponse(portalUrl, target.targetPath());
    }

    private SsoSignoutResponse signout(SsoSignoutScope scope) {
        SsoClientLocalSession session = requiredSession();
        SsoLocalOutcome localOutcome = revokeLocal(session, scope);
        if (scope == SsoSignoutScope.CURRENT_DEVICE
                && (session.deviceId() == null || session.deviceId().isBlank())) {
            return notRequested(localOutcome, SsoPropagationReason.DEVICE_INVALID);
        }

        Optional<Long> mapping;
        try {
            mapping = identityAdapter.findSsoUserId(session.localUserId());
        } catch (RuntimeException exception) {
            return notRequested(localOutcome, SsoPropagationReason.IDENTITY_LOOKUP_FAILED);
        }
        if (mapping == null || mapping.isEmpty()) {
            return notRequested(localOutcome, SsoPropagationReason.NO_MAPPING);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(SsoProtocolFields.SSO_USER_ID, mapping.get());
        payload.put("scope", scope.name());
        if (scope == SsoSignoutScope.CURRENT_DEVICE) {
            payload.put("deviceId", session.deviceId());
        }
        try {
            SsoMessageResult<SsoSignoutReceipt> result = messageGateway.send(
                    SsoMessageTypes.REQUEST_SLO,
                    payload,
                    SsoSignoutReceipt.class);
            if (result == null) {
                return unconfirmed(localOutcome);
            }
            if (!result.successful()) {
                return new SsoSignoutResponse(
                        localOutcome,
                        SsoPropagationOutcome.REJECTED,
                        null,
                        null);
            }
            if (result.data() == null) {
                return unconfirmed(localOutcome);
            }
            return new SsoSignoutResponse(
                    localOutcome,
                    SsoPropagationOutcome.ACCEPTED,
                    null,
                    result.data());
        } catch (RuntimeException exception) {
            log.warn("[SSO] SLO 传播请求结果不确定, scope={}", scope);
            return unconfirmed(localOutcome);
        }
    }

    private SsoClientLocalSession requiredSession() {
        SsoClientLocalSession session;
        try {
            session = localSessions.current();
        } catch (RuntimeException exception) {
            throw serverFailure();
        }
        if (session == null || !session.authenticated()) {
            throw new SsoClientWebException(
                    SsoWebCodes.SESSION_REQUIRED,
                    401,
                    "当前 Client 未登录");
        }
        return session;
    }

    private SsoLocalOutcome revokeLocal(
            SsoClientLocalSession session, SsoSignoutScope scope) {
        try {
            SsoLocalOutcome outcome = scope == SsoSignoutScope.CURRENT_DEVICE
                    ? localSessions.revokeDevice(session.localUserId(), session.deviceId())
                    : localSessions.revokeAccount(session.localUserId());
            return outcome == null ? SsoLocalOutcome.FAILED : outcome;
        } catch (RuntimeException exception) {
            log.warn("[SSO] Client 本地会话撤销失败, scope={}", scope);
            return SsoLocalOutcome.FAILED;
        }
    }

    private SsoLocalOutcome revokeExact(SsoClientLocalSession session) {
        try {
            SsoLocalOutcome outcome = localSessions.revokeExact(session.sessionHandle());
            return outcome == null ? SsoLocalOutcome.FAILED : outcome;
        } catch (RuntimeException exception) {
            log.warn("[SSO] Client 当前精确 Session 撤销失败");
            return SsoLocalOutcome.FAILED;
        }
    }

    private void compensateIfEstablished(SsoClientLoginResult result) {
        if (result == null) {
            return;
        }
        try {
            SsoLocalOutcome outcome = localSessions.revokeExact(result.sessionHandle());
            if (outcome == SsoLocalOutcome.FAILED) {
                log.error("[SSO] Browser 登录事务未能完成，精确 Session 补偿失败");
            }
        } catch (RuntimeException exception) {
            log.error("[SSO] Browser 登录事务未能完成，精确 Session 补偿抛出异常");
        }
    }

    private static void validateCallback(SsoLoginCallbackRequest request) {
        if (request.ticket() == null || request.ticket().isBlank()
                || request.ticket().length() > MAX_TICKET_LENGTH) {
            throw new SsoClientWebException(
                    SsoWebCodes.REQUEST_INVALID,
                    400,
                    "ticket 字段非法");
        }
        if (request.state() == null || !STATE.matcher(request.state()).matches()) {
            throw new SsoClientWebException(
                    SsoWebCodes.REQUEST_INVALID,
                    400,
                    "state 字段非法");
        }
    }

    private static String validateTheme(String theme) {
        if (theme == null) {
            return null;
        }
        if (!"light".equals(theme) && !"dark".equals(theme)) {
            throw new SsoClientWebException(
                    SsoWebCodes.REQUEST_INVALID,
                    400,
                    "theme 只允许 light 或 dark");
        }
        return theme;
    }

    private static SsoSignoutResponse notRequested(
            SsoLocalOutcome localOutcome, SsoPropagationReason reason) {
        return new SsoSignoutResponse(
                localOutcome,
                SsoPropagationOutcome.NOT_REQUESTED,
                reason,
                null);
    }

    private static SsoSignoutResponse unconfirmed(SsoLocalOutcome localOutcome) {
        return new SsoSignoutResponse(
                localOutcome,
                SsoPropagationOutcome.UNCONFIRMED,
                null,
                null);
    }

    private static SsoClientWebException redirectInvalid() {
        return new SsoClientWebException(
                SsoWebCodes.REDIRECT_INVALID,
                400,
                "back 不是安全的 Client 回跳地址");
    }

    private static SsoClientWebException serverFailure() {
        return new SsoClientWebException(
                SsoWebCodes.SERVER_FAILURE,
                502,
                "SSO 操作未形成可信结果");
    }

    private static String firstStackFrame(RuntimeException exception) {
        StackTraceElement[] stackTrace = exception.getStackTrace();
        return stackTrace.length == 0 ? "unknown" : stackTrace[0].toString();
    }
}
