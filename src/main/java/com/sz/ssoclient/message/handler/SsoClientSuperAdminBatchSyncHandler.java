package com.sz.ssoclient.message.handler;

import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.sso.template.SaSsoTemplate;
import cn.dev33.satoken.util.SaResult;
import com.sz.ssoclient.message.SsoServerMessageHandler;
import com.sz.ssoclient.spi.SsoRoleBindingService;
import com.sz.ssoclient.spi.SsoUserMappingService;
import com.sz.ssocore.SsoMessageTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server 批量推送客户端超管状态变更的标准处理器.
 */
@Slf4j
@RequiredArgsConstructor
public class SsoClientSuperAdminBatchSyncHandler implements SsoServerMessageHandler {

    private static final String KEY_CENTER_IDS = "centerIds";
    private static final String KEY_CLIENT_ID = "clientId";
    private static final String KEY_IS_SUPER_ADMIN = "isSuperAdmin";

    private final SsoUserMappingService ssoUserMappingService;
    private final SsoRoleBindingService ssoRoleBindingService;

    @Override
    public String messageType() {
        return SsoMessageTypes.SYNC_CLIENT_SUPER_ADMIN_BATCH;
    }

    @Override
    public SaResult handle(SaSsoTemplate template, SaSsoMessage message) {
        List<Object> centerIds = parseCenterIds(message.get(KEY_CENTER_IDS));
        Object isSuperAdminObj = message.get(KEY_IS_SUPER_ADMIN);
        Object clientIdObj = message.get(KEY_CLIENT_ID);
        if (centerIds.isEmpty() || isSuperAdminObj == null) {
            log.warn("[SSO] Server 批量推送超管同步消息缺少必要参数, centerIds={}, isSuperAdmin={}, clientId={}",
                    centerIds, isSuperAdminObj, clientIdObj);
            return SaResult.error("centerIds and isSuperAdmin are required");
        }

        Map<Object, Object> localUserIds;
        try {
            localUserIds = ssoUserMappingService.resolveExistingClientUsers(centerIds);
        } catch (Exception e) {
            log.warn("[SSO] Server 批量推送超管同步时，centerIds 转本地用户失败. centerIds={}, clientId={}", centerIds, clientIdObj, e);
            return SaResult.error("centerIds convert failed");
        }

        boolean isSuperAdmin = Boolean.parseBoolean(isSuperAdminObj.toString());
        List<String> successIds = new ArrayList<>();
        List<Map<String, Object>> failItems = new ArrayList<>();
        for (int i = 0; i < centerIds.size(); i++) {
            Object centerId = centerIds.get(i);
            Object localUserIdObj = findLocalUserId(localUserIds, centerId);
            if (localUserIdObj == null) {
                failItems.add(failItem(i, centerId, "local user mapping not found"));
                continue;
            }
            Long localUserId;
            try {
                localUserId = Long.valueOf(localUserIdObj.toString());
            } catch (Exception e) {
                log.warn("[SSO] Server 批量推送超管同步时，本地用户 ID 非法. centerId={}, localUserId={}, clientId={}",
                        centerId, localUserIdObj, clientIdObj, e);
                failItems.add(failItem(i, centerId, "local user id invalid"));
                continue;
            }
            try {
                ssoRoleBindingService.applySuperAdmin(localUserId, isSuperAdmin);
                successIds.add(centerId.toString());
            } catch (Exception e) {
                log.warn("[SSO] Server 批量推送超管同步落库失败. centerId={}, localUserId={}, clientId={}, isSuperAdmin={}",
                        centerId, localUserId, clientIdObj, isSuperAdmin, e);
                failItems.add(failItem(i, centerId, "apply super admin failed"));
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requested", centerIds.size());
        data.put("success", successIds.size());
        data.put("failed", failItems.size());
        data.put("successIds", successIds);
        data.put("failItems", failItems);
        log.info("[SSO] Server 批量推送超管同步完成. clientId={}, requested={}, success={}, failed={}, isSuperAdmin={}",
                clientIdObj, centerIds.size(), successIds.size(), failItems.size(), isSuperAdmin);
        return SaResult.data(data);
    }

    private static Object findLocalUserId(Map<Object, Object> localUserIds, Object centerId) {
        if (localUserIds == null || localUserIds.isEmpty()) {
            return null;
        }
        Object value = localUserIds.get(centerId);
        if (value != null) {
            return value;
        }
        value = localUserIds.get(centerId.toString());
        if (value != null) {
            return value;
        }
        for (Map.Entry<Object, Object> entry : localUserIds.entrySet()) {
            if (entry.getKey() != null && centerId.toString().equals(entry.getKey().toString())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static List<Object> parseCenterIds(Object value) {
        List<Object> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        if (value instanceof Collection<?> collection) {
            collection.stream().filter(item -> item != null && !item.toString().isBlank()).forEach(result::add);
            return result;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                Object item = Array.get(value, i);
                if (item != null && !item.toString().isBlank()) {
                    result.add(item);
                }
            }
            return result;
        }
        for (String item : value.toString().split(",")) {
            String centerId = item.trim();
            if (!centerId.isEmpty()) {
                result.add(centerId);
            }
        }
        return result;
    }

    private static Map<String, Object> failItem(int index, Object centerId, String reason) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("index", index + 1);
        item.put("centerId", centerId == null ? null : centerId.toString());
        item.put("reason", reason);
        return item;
    }
}