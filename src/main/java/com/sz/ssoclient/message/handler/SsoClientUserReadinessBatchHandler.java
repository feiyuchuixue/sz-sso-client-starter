package com.sz.ssoclient.message.handler;

import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.sso.template.SaSsoTemplate;
import cn.dev33.satoken.util.SaResult;
import com.sz.ssoclient.message.SsoServerMessageHandler;
import com.sz.ssoclient.spi.SsoClientUserProvisioningService;
import com.sz.ssoclient.spi.SsoUserMappingService;
import com.sz.ssocore.SsoClientSyncFailureCodes;
import com.sz.ssocore.SsoMessageTypes;
import com.sz.ssocore.provisioning.SsoClientGrantPurpose;
import com.sz.ssocore.provisioning.SsoClientUserReadinessBatchResult;
import com.sz.ssocore.provisioning.SsoClientUserReadinessItem;
import com.sz.ssocore.provisioning.SsoClientUserReadinessStatus;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Server 批量检查目标 Client 用户就绪状态的标准处理器。 */
@Slf4j
public class SsoClientUserReadinessBatchHandler implements SsoServerMessageHandler {

    private static final String KEY_CENTER_IDS = "centerIds";
    private static final String KEY_PURPOSE = "purpose";

    private final SsoUserMappingService ssoUserMappingService;
    private final SsoClientUserProvisioningService provisioningService;

    public SsoClientUserReadinessBatchHandler(SsoUserMappingService ssoUserMappingService,
                                              SsoClientUserProvisioningService provisioningService) {
        this.ssoUserMappingService = ssoUserMappingService;
        this.provisioningService = provisioningService;
    }

    @Override
    public String messageType() {
        return SsoMessageTypes.CHECK_CLIENT_USER_READINESS_BATCH;
    }

    @Override
    public SaResult handle(SaSsoTemplate template, SaSsoMessage message) {
        List<Long> centerIds = parseCenterIds(message.get(KEY_CENTER_IDS));
        SsoClientGrantPurpose purpose = parsePurpose(message.get(KEY_PURPOSE));
        if (purpose == null) {
            return SaResult.data(checkFailed(centerIds));
        }
        if (provisioningService != null) {
            try {
                SsoClientUserReadinessBatchResult result = provisioningService.checkUsers(centerIds, purpose);
                return SaResult.data(result == null ? checkFailed(centerIds) : result);
            } catch (Exception e) {
                log.warn("[SSO] Client readiness 检查异常, count={}", centerIds.size(), e);
                return SaResult.data(checkFailed(centerIds));
            }
        }
        return SaResult.data(fallbackCheck(centerIds));
    }

    /**
     * 历史 Client 未接入准备 SPI 时只能读取既有映射；不得调用登录链路的 resolveOrProvisionClientUser，
     * 以保证 readiness 检查不产生本地账号或映射写入。
     */
    private SsoClientUserReadinessBatchResult fallbackCheck(List<Long> centerIds) {
        List<SsoClientUserReadinessItem> items = new ArrayList<>();
        int readyCount = 0;
        for (Long centerId : centerIds) {
            try {
                Object localUserId = ssoUserMappingService.toClientUserId(centerId);
                if (localUserId == null) {
                    items.add(SsoClientUserReadinessItem.builder()
                            .ssoUserId(centerId)
                            .status(SsoClientUserReadinessStatus.UNSUPPORTED)
                            .reasonCode(SsoClientSyncFailureCodes.CLIENT_USER_PROVISION_UNSUPPORTED)
                            .build());
                    continue;
                }
                items.add(SsoClientUserReadinessItem.builder()
                        .ssoUserId(centerId)
                        .localUserId(Long.valueOf(localUserId.toString()))
                        .status(SsoClientUserReadinessStatus.READY)
                        .build());
                readyCount++;
            } catch (Exception e) {
                log.warn("[SSO] 查询既有 Client 用户映射失败, centerId={}", centerId, e);
                items.add(checkFailedItem(centerId));
            }
        }
        return SsoClientUserReadinessBatchResult.builder()
                .submittedCount(centerIds.size())
                .readyCount(readyCount)
                .items(items)
                .build();
    }

    private static SsoClientUserReadinessBatchResult checkFailed(List<Long> centerIds) {
        return SsoClientUserReadinessBatchResult.builder()
                .submittedCount(centerIds.size())
                .items(centerIds.stream().map(SsoClientUserReadinessBatchHandler::checkFailedItem).toList())
                .build();
    }

    private static SsoClientUserReadinessItem checkFailedItem(Long centerId) {
        return SsoClientUserReadinessItem.builder()
                .ssoUserId(centerId)
                .status(SsoClientUserReadinessStatus.CHECK_FAILED)
                .reasonCode(SsoClientSyncFailureCodes.CLIENT_USER_READINESS_CHECK_FAILED)
                .build();
    }

    private static SsoClientGrantPurpose parsePurpose(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return SsoClientGrantPurpose.valueOf(value.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<Long> parseCenterIds(Object value) {
        List<Long> result = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            collection.forEach(item -> addCenterId(result, item));
        } else if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                addCenterId(result, Array.get(value, i));
            }
        } else if (value != null) {
            for (String item : value.toString().split(",")) {
                addCenterId(result, item);
            }
        }
        return result;
    }

    private static void addCenterId(List<Long> result, Object value) {
        if (value == null || value.toString().isBlank()) {
            return;
        }
        try {
            result.add(Long.valueOf(value.toString().trim()));
        } catch (NumberFormatException ignored) {
            // 非法 ID 不进入 Client 查询，Server 将其视为协议参数错误处理。
        }
    }
}
