package com.sz.ssoclient.internal.messaging.handler;

import com.sz.ssoclient.internal.messaging.SsoClientMessageRegistrar;
import com.sz.ssoclient.internal.messaging.SsoMessageCodec;
import com.sz.ssoclient.spi.SsoClientIdentityAdapter;
import com.sz.ssoclient.spi.SsoSuperAdminAuthorityAdapter;
import com.sz.ssocore.SsoMessageResult;
import com.sz.ssocore.SsoMessageTypes;
import com.sz.ssocore.SsoProtocolFields;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Server 下发批量 SSO 超管变更并保留逐项结果的固定 Handler。 */
@Slf4j
public final class SsoClientSuperAdminBatchSyncHandler
        implements SsoClientMessageRegistrar.FixedHandler {

    private final SsoClientIdentityAdapter identityAdapter;
    private final SsoSuperAdminAuthorityAdapter authorityAdapter;

    public SsoClientSuperAdminBatchSyncHandler(
            SsoClientIdentityAdapter identityAdapter,
            SsoSuperAdminAuthorityAdapter authorityAdapter) {
        this.identityAdapter = Objects.requireNonNull(identityAdapter, "identityAdapter");
        this.authorityAdapter = authorityAdapter;
    }

    @Override
    public String messageType() {
        return SsoMessageTypes.SYNC_CLIENT_SUPER_ADMIN_BATCH;
    }

    @Override
    public SsoMessageResult<Map<String, Object>> handle(Map<String, Object> payload) {
        final List<Long> ssoUserIds;
        final boolean superAdmin;
        try {
            ssoUserIds = SsoMessageCodec.requiredLongs(payload, SsoProtocolFields.CENTER_IDS);
            superAdmin = SsoMessageCodec.requiredBoolean(payload, SsoProtocolFields.IS_SUPER_ADMIN);
        } catch (RuntimeException exception) {
            return new SsoMessageResult<>("SUPER_ADMIN_BATCH_REQUEST_INVALID", exception.getMessage(), null);
        }
        List<Long> successIds = new ArrayList<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        for (int index = 0; index < ssoUserIds.size(); index++) {
            Long ssoUserId = ssoUserIds.get(index);
            String reason = applyOne(ssoUserId, superAdmin);
            if (reason == null) {
                successIds.add(ssoUserId);
            } else {
                Map<String, Object> failure = new LinkedHashMap<>();
                failure.put("index", index + 1);
                failure.put(SsoProtocolFields.SSO_USER_ID, ssoUserId);
                failure.put("reason", reason);
                failures.add(Map.copyOf(failure));
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requested", ssoUserIds.size());
        data.put("success", successIds.size());
        data.put("failed", failures.size());
        data.put("successIds", List.copyOf(successIds));
        data.put("failItems", List.copyOf(failures));
        return new SsoMessageResult<>(SsoMessageResult.SUCCESS_CODE, "Client SSO 超管批量结果已形成", Map.copyOf(data));
    }

    private String applyOne(Long ssoUserId, boolean superAdmin) {
        if (authorityAdapter == null) {
            return "Client 未接入 SSO 超管本地权限能力";
        }
        try {
            Optional<String> localUserId = identityAdapter.findLocalUserId(ssoUserId);
            if (localUserId == null || localUserId.isEmpty()) {
                return "Client 不存在目标 SSO 用户映射";
            }
            authorityAdapter.apply(localUserId.get(), superAdmin);
            return null;
        } catch (RuntimeException exception) {
            log.warn("[SSO] Client SSO 超管批量单项应用失败, ssoUserId={}", ssoUserId, exception);
            return "Client SSO 超管状态应用失败";
        }
    }
}
