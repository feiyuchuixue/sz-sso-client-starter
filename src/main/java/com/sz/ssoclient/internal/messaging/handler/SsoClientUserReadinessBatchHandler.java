package com.sz.ssoclient.internal.messaging.handler;

import com.sz.ssoclient.internal.messaging.SsoClientMessageRegistrar;
import com.sz.ssoclient.internal.messaging.SsoClientMessageGateway;
import com.sz.ssoclient.internal.messaging.SsoMessageCodec;
import com.sz.ssoclient.spi.SsoClientIdentityAdapter;
import com.sz.ssoclient.spi.SsoClientIdentityPreparationService;
import com.sz.ssocore.SsoClientSyncFailureCodes;
import com.sz.ssocore.SsoMessageResult;
import com.sz.ssocore.SsoMessageTypes;
import com.sz.ssocore.SsoProtocolFields;
import com.sz.ssocore.provisioning.SsoClientGrantPurpose;
import com.sz.ssocore.provisioning.SsoClientUserReadinessBatchResult;
import com.sz.ssocore.provisioning.SsoClientUserReadinessItem;
import com.sz.ssocore.provisioning.SsoClientUserReadinessStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Server 批量只读检查 Client 用户身份准备条件的固定 Handler。 */
@Slf4j
public final class SsoClientUserReadinessBatchHandler
        implements SsoClientMessageRegistrar.FixedHandler {

    private final SsoClientIdentityAdapter identityAdapter;
    private final SsoClientIdentityPreparationService preparationService;
    private final SsoClientMessageGateway messageGateway;

    public SsoClientUserReadinessBatchHandler(
            SsoClientIdentityAdapter identityAdapter,
            SsoClientIdentityPreparationService preparationService,
            SsoClientMessageGateway messageGateway) {
        this.identityAdapter = Objects.requireNonNull(identityAdapter, "identityAdapter");
        this.preparationService = preparationService;
        this.messageGateway = Objects.requireNonNull(messageGateway, "messageGateway");
    }

    @Override
    public String messageType() {
        return SsoMessageTypes.CHECK_CLIENT_USER_READINESS_BATCH;
    }

    @Override
    public SsoMessageResult<SsoClientUserReadinessBatchResult> handle(Map<String, Object> payload) {
        List<Long> ssoUserIds;
        SsoClientGrantPurpose purpose;
        try {
            ssoUserIds = SsoMessageCodec.requiredLongs(payload, SsoProtocolFields.CENTER_IDS);
            purpose = SsoClientGrantPurpose.valueOf(
                    SsoMessageCodec.requiredText(payload, SsoProtocolFields.PURPOSE));
        } catch (RuntimeException exception) {
            return failure("CLIENT_READINESS_REQUEST_INVALID", exception.getMessage());
        }

        if (preparationService != null) {
            try {
                SsoClientUserReadinessBatchResult result = preparationService.checkUsers(
                        messageGateway.fetchUserMetas(ssoUserIds), purpose);
                if (result == null) {
                    return failure("CLIENT_READINESS_RESULT_MISSING", "Client readiness SPI 返回了 null");
                }
                return success(result);
            } catch (RuntimeException exception) {
                log.warn("[SSO] Client readiness SPI 执行失败, count={}, purpose={}",
                        ssoUserIds.size(), purpose, exception);
                return failure("CLIENT_READINESS_CHECK_FAILED", "Client readiness 检查失败");
            }
        }
        return success(readExistingMappings(ssoUserIds));
    }

    private SsoClientUserReadinessBatchResult readExistingMappings(List<Long> ssoUserIds) {
        List<SsoClientUserReadinessItem> items = new ArrayList<>(ssoUserIds.size());
        int readyCount = 0;
        for (Long ssoUserId : ssoUserIds) {
            try {
                Optional<String> localUserId = identityAdapter.findLocalUserId(ssoUserId);
                if (localUserId != null && localUserId.isPresent()) {
                    items.add(SsoClientUserReadinessItem.builder()
                            .ssoUserId(ssoUserId)
                            .localUserId(localUserId.get())
                            .status(SsoClientUserReadinessStatus.READY)
                            .preparable(false)
                            .build());
                    readyCount++;
                } else {
                    items.add(SsoClientUserReadinessItem.builder()
                            .ssoUserId(ssoUserId)
                            .status(SsoClientUserReadinessStatus.UNSUPPORTED)
                            .preparable(false)
                            .reasonCode(SsoClientSyncFailureCodes.CLIENT_USER_PROVISION_UNSUPPORTED)
                            .reason("Client 未接入身份准备 SPI，且不存在既有映射")
                            .build());
                }
            } catch (RuntimeException exception) {
                log.warn("[SSO] 读取既有 Client 用户映射失败, ssoUserId={}", ssoUserId, exception);
                items.add(SsoClientUserReadinessItem.builder()
                        .ssoUserId(ssoUserId)
                        .status(SsoClientUserReadinessStatus.CHECK_FAILED)
                        .preparable(false)
                        .reasonCode(SsoClientSyncFailureCodes.CLIENT_USER_READINESS_CHECK_FAILED)
                        .reason("读取既有映射失败")
                        .build());
            }
        }
        return SsoClientUserReadinessBatchResult.builder()
                .submittedCount(ssoUserIds.size())
                .readyCount(readyCount)
                .preparableCount(0)
                .items(items)
                .build();
    }

    private static SsoMessageResult<SsoClientUserReadinessBatchResult> success(
            SsoClientUserReadinessBatchResult data) {
        return new SsoMessageResult<>(SsoMessageResult.SUCCESS_CODE, "Client readiness 结果已形成", data);
    }

    private static SsoMessageResult<SsoClientUserReadinessBatchResult> failure(String code, String message) {
        return new SsoMessageResult<>(code, message, null);
    }
}
