package com.sz.ssoclient.message.handler;

import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.sso.template.SaSsoTemplate;
import cn.dev33.satoken.util.SaResult;
import com.sz.ssoclient.message.SsoServerMessageHandler;
import com.sz.ssoclient.spi.SsoClientUserProvisioningService;
import com.sz.ssocore.SsoClientSyncFailureCodes;
import com.sz.ssocore.SsoMessageTypes;
import com.sz.ssocore.provisioning.SsoClientGrantPurpose;
import com.sz.ssocore.provisioning.SsoClientUserPreparationBatchResult;
import com.sz.ssocore.provisioning.SsoClientUserPreparationItem;
import com.sz.ssocore.provisioning.SsoClientUserPreparationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Server 请求目标 Client 准备本地用户的标准处理器。 */
@Slf4j
@RequiredArgsConstructor
public class SsoClientUserPreparationBatchHandler implements SsoServerMessageHandler {

    private static final String KEY_CENTER_IDS = "centerIds";
    private static final String KEY_PURPOSE = "purpose";

    private final SsoClientUserProvisioningService provisioningService;

    @Override
    public String messageType() {
        return SsoMessageTypes.PREPARE_CLIENT_USERS_BATCH;
    }

    @Override
    public SaResult handle(SaSsoTemplate template, SaSsoMessage message) {
        List<Long> centerIds = parseCenterIds(message.get(KEY_CENTER_IDS));
        SsoClientGrantPurpose purpose = parsePurpose(message.get(KEY_PURPOSE));
        List<SsoClientUserPreparationItem> items = new ArrayList<>();
        int preparedCount = 0;
        int alreadyReadyCount = 0;
        int failedCount = 0;

        for (Long centerId : centerIds) {
            SsoClientUserPreparationItem item = prepareOne(centerId, purpose);
            items.add(item);
            if (item.getStatus() == SsoClientUserPreparationStatus.PREPARED) {
                preparedCount++;
            } else if (item.getStatus() == SsoClientUserPreparationStatus.ALREADY_READY) {
                alreadyReadyCount++;
            } else {
                failedCount++;
            }
        }
        return SaResult.data(SsoClientUserPreparationBatchResult.builder()
                .submittedCount(centerIds.size())
                .preparedCount(preparedCount)
                .alreadyReadyCount(alreadyReadyCount)
                .failedCount(failedCount)
                .items(items)
                .build());
    }

    /** 单项隔离调用 SPI，避免一个 Client 用户的准备异常阻断同批其他用户。 */
    private SsoClientUserPreparationItem prepareOne(Long centerId, SsoClientGrantPurpose purpose) {
        if (purpose == null) {
            return failedItem(centerId);
        }
        try {
            SsoClientUserPreparationBatchResult result = provisioningService.prepareUsers(List.of(centerId), purpose);
            if (result == null || result.getItems() == null) {
                return failedItem(centerId);
            }
            return result.getItems().stream()
                    .filter(item -> centerId.equals(item.getSsoUserId()))
                    .findFirst()
                    .orElseGet(() -> failedItem(centerId));
        } catch (Exception e) {
            log.warn("[SSO] Client 用户准备失败, centerId={}", centerId, e);
            return failedItem(centerId);
        }
    }

    private static SsoClientUserPreparationItem failedItem(Long centerId) {
        return SsoClientUserPreparationItem.builder()
                .ssoUserId(centerId)
                .status(SsoClientUserPreparationStatus.FAILED)
                .reasonCode(SsoClientSyncFailureCodes.CLIENT_USER_PREPARE_FAILED)
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
            // 非法 ID 不进入 Client 准备，Server 将其视为协议参数错误处理。
        }
    }
}
