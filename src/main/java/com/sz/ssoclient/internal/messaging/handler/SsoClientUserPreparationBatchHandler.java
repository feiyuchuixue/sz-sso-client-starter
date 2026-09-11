package com.sz.ssoclient.internal.messaging.handler;

import com.sz.ssoclient.internal.messaging.SsoClientMessageRegistrar;
import com.sz.ssoclient.internal.messaging.SsoClientMessageGateway;
import com.sz.ssoclient.internal.messaging.SsoMessageCodec;
import com.sz.ssoclient.spi.SsoClientIdentityPreparationService;
import com.sz.ssocore.SsoClientSyncFailureCodes;
import com.sz.ssocore.SsoMessageResult;
import com.sz.ssocore.SsoMessageTypes;
import com.sz.ssocore.SsoProtocolFields;
import com.sz.ssocore.provisioning.SsoClientGrantPurpose;
import com.sz.ssocore.provisioning.SsoClientUserPreparationBatchResult;
import com.sz.ssocore.provisioning.SsoClientUserPreparationItem;
import com.sz.ssocore.provisioning.SsoClientUserPreparationStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/** Server 请求 Client 批量执行显式身份准备的固定 Handler。 */
@Slf4j
public final class SsoClientUserPreparationBatchHandler
        implements SsoClientMessageRegistrar.FixedHandler {

    private final SsoClientIdentityPreparationService preparationService;
    private final SsoClientMessageGateway messageGateway;

    public SsoClientUserPreparationBatchHandler(
            SsoClientIdentityPreparationService preparationService,
            SsoClientMessageGateway messageGateway) {
        this.preparationService = preparationService;
        this.messageGateway = java.util.Objects.requireNonNull(messageGateway, "messageGateway");
    }

    @Override
    public String messageType() {
        return SsoMessageTypes.PREPARE_CLIENT_USERS_BATCH;
    }

    @Override
    public SsoMessageResult<SsoClientUserPreparationBatchResult> handle(Map<String, Object> payload) {
        final List<Long> ssoUserIds;
        final SsoClientGrantPurpose purpose;
        try {
            ssoUserIds = SsoMessageCodec.requiredLongs(payload, SsoProtocolFields.CENTER_IDS);
            purpose = SsoClientGrantPurpose.valueOf(
                    SsoMessageCodec.requiredText(payload, SsoProtocolFields.PURPOSE));
        } catch (RuntimeException exception) {
            return new SsoMessageResult<>("CLIENT_PREPARATION_REQUEST_INVALID", exception.getMessage(), null);
        }

        if (preparationService == null) {
            return success(allFailed(
                    ssoUserIds,
                    SsoClientSyncFailureCodes.CLIENT_USER_PROVISION_UNSUPPORTED,
                    "Client 未接入身份准备 SPI"));
        }
        try {
            SsoClientUserPreparationBatchResult result = preparationService.prepareUsers(
                    messageGateway.fetchUserMetas(ssoUserIds), purpose);
            if (result == null) {
                return success(allFailed(
                        ssoUserIds,
                        SsoClientSyncFailureCodes.CLIENT_USER_PREPARE_FAILED,
                        "Client 身份准备 SPI 返回了 null"));
            }
            return success(result);
        } catch (RuntimeException exception) {
            log.warn("[SSO] Client 批量身份准备失败, count={}, purpose={}",
                    ssoUserIds.size(), purpose, exception);
            return success(allFailed(
                    ssoUserIds,
                    SsoClientSyncFailureCodes.CLIENT_USER_PREPARE_FAILED,
                    "Client 用户身份准备失败"));
        }
    }

    private static SsoClientUserPreparationBatchResult allFailed(
            List<Long> ssoUserIds,
            String reasonCode,
            String reason) {
        List<SsoClientUserPreparationItem> items = ssoUserIds.stream()
                .map(ssoUserId -> SsoClientUserPreparationItem.builder()
                        .ssoUserId(ssoUserId)
                        .status(SsoClientUserPreparationStatus.FAILED)
                        .reasonCode(reasonCode)
                        .reason(reason)
                        .build())
                .toList();
        return SsoClientUserPreparationBatchResult.builder()
                .submittedCount(ssoUserIds.size())
                .failedCount(ssoUserIds.size())
                .items(items)
                .build();
    }

    private static SsoMessageResult<SsoClientUserPreparationBatchResult> success(
            SsoClientUserPreparationBatchResult data) {
        return new SsoMessageResult<>(SsoMessageResult.SUCCESS_CODE, "Client 身份准备结果已形成", data);
    }
}
