package com.sz.ssoclient.internal.messaging.handler;

import com.sz.ssoclient.internal.messaging.SsoClientMessageRegistrar;
import com.sz.ssoclient.internal.messaging.SsoMessageCodec;
import com.sz.ssoclient.spi.SsoClientIdentityAdapter;
import com.sz.ssoclient.spi.advanced.SsoClientLocalSessionAccessor;
import com.sz.ssocore.SsoMessageResult;
import com.sz.ssocore.SsoMessageTypes;
import com.sz.ssocore.SsoProtocolFields;
import com.sz.ssocore.signout.SsoClientRevocationOutcome;
import com.sz.ssocore.signout.SsoLocalOutcome;
import com.sz.ssocore.signout.SsoSignoutScope;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Server 请求幂等撤销 Client 本地 SSO 会话的固定 Handler。 */
@Slf4j
public final class SsoClientSessionRevocationHandler
        implements SsoClientMessageRegistrar.FixedHandler {

    private static final String SIGNOUT_ID = "signoutId";
    private static final String SCOPE = "scope";
    private static final String DEVICE_ID = "deviceId";

    private final SsoClientIdentityAdapter identityAdapter;
    private final SsoClientLocalSessionAccessor localSessionAccessor;

    public SsoClientSessionRevocationHandler(
            SsoClientIdentityAdapter identityAdapter,
            SsoClientLocalSessionAccessor localSessionAccessor) {
        this.identityAdapter = Objects.requireNonNull(identityAdapter, "identityAdapter");
        this.localSessionAccessor = Objects.requireNonNull(localSessionAccessor, "localSessionAccessor");
    }

    @Override
    public String messageType() {
        return SsoMessageTypes.REVOKE_SSO_SESSIONS;
    }

    @Override
    public SsoMessageResult<SsoClientRevocationOutcome> handle(Map<String, Object> payload) {
        try {
            SsoMessageCodec.requiredText(payload, SIGNOUT_ID);
            long ssoUserId = SsoMessageCodec.requiredLong(payload, SsoProtocolFields.SSO_USER_ID);
            SsoSignoutScope scope = SsoSignoutScope.valueOf(SsoMessageCodec.requiredText(payload, SCOPE));
            Optional<String> localUserId = identityAdapter.findLocalUserId(ssoUserId);
            if (localUserId == null || localUserId.isEmpty()) {
                return success(SsoClientRevocationOutcome.NOT_APPLICABLE);
            }
            SsoLocalOutcome localOutcome = scope == SsoSignoutScope.CURRENT_DEVICE
                    ? localSessionAccessor.revokeDevice(
                            localUserId.get(),
                            SsoMessageCodec.requiredText(payload, DEVICE_ID))
                    : localSessionAccessor.revokeAccount(localUserId.get());
            return success(switch (localOutcome) {
                case REVOKED -> SsoClientRevocationOutcome.REVOKED;
                case ALREADY_REVOKED -> SsoClientRevocationOutcome.ALREADY_REVOKED;
                case FAILED -> SsoClientRevocationOutcome.FAILED;
            });
        } catch (RuntimeException exception) {
            log.warn("[SSO] Client 本地 SSO 会话撤销失败", exception);
            return new SsoMessageResult<>(
                    "CLIENT_SESSION_REVOCATION_FAILED",
                    "Client 本地 SSO 会话撤销失败",
                    SsoClientRevocationOutcome.FAILED);
        }
    }

    private static SsoMessageResult<SsoClientRevocationOutcome> success(SsoClientRevocationOutcome data) {
        return new SsoMessageResult<>(SsoMessageResult.SUCCESS_CODE, "Client 会话撤销结果已形成", data);
    }
}
