package com.sz.ssoclient.internal.messaging.handler;

import com.sz.ssoclient.internal.messaging.SsoClientMessageRegistrar;
import com.sz.ssoclient.internal.messaging.SsoMessageCodec;
import com.sz.ssoclient.spi.SsoClientIdentityAdapter;
import com.sz.ssoclient.spi.SsoSuperAdminAuthorityAdapter;
import com.sz.ssocore.SsoMessageResult;
import com.sz.ssocore.SsoMessageTypes;
import com.sz.ssocore.SsoProtocolFields;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Server 下发单个 SSO 超管变更的固定 Handler。 */
@Slf4j
public final class SsoClientSuperAdminSyncHandler
        implements SsoClientMessageRegistrar.FixedHandler {

    private final SsoClientIdentityAdapter identityAdapter;
    private final SsoSuperAdminAuthorityAdapter authorityAdapter;

    public SsoClientSuperAdminSyncHandler(
            SsoClientIdentityAdapter identityAdapter,
            SsoSuperAdminAuthorityAdapter authorityAdapter) {
        this.identityAdapter = Objects.requireNonNull(identityAdapter, "identityAdapter");
        this.authorityAdapter = authorityAdapter;
    }

    @Override
    public String messageType() {
        return SsoMessageTypes.SYNC_CLIENT_SUPER_ADMIN;
    }

    @Override
    public SsoMessageResult<Void> handle(Map<String, Object> payload) {
        try {
            long ssoUserId = SsoMessageCodec.requiredLong(payload, SsoProtocolFields.SSO_USER_ID);
            boolean superAdmin = SsoMessageCodec.requiredBoolean(payload, SsoProtocolFields.IS_SUPER_ADMIN);
            if (authorityAdapter == null) {
                return failure("SUPER_ADMIN_AUTHORITY_UNSUPPORTED", "Client 未接入 SSO 超管本地权限能力");
            }
            Optional<String> localUserId = identityAdapter.findLocalUserId(ssoUserId);
            if (localUserId == null || localUserId.isEmpty()) {
                return failure("SSO_USER_MAPPING_NOT_FOUND", "Client 不存在目标 SSO 用户映射");
            }
            authorityAdapter.apply(localUserId.get(), superAdmin);
            return new SsoMessageResult<>(SsoMessageResult.SUCCESS_CODE, "Client SSO 超管状态已应用", null);
        } catch (RuntimeException exception) {
            log.warn("[SSO] Client SSO 超管同步失败", exception);
            return failure("CLIENT_GRANT_APPLY_FAILED", "Client SSO 超管状态应用失败");
        }
    }

    private static SsoMessageResult<Void> failure(String code, String message) {
        return new SsoMessageResult<>(code, message, null);
    }
}
