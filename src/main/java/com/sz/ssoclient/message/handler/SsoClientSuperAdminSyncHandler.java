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

/**
 * Server 推送客户端超管状态变更的标准处理器.
 */
@Slf4j
@RequiredArgsConstructor
public class SsoClientSuperAdminSyncHandler implements SsoServerMessageHandler {

    private static final String KEY_CENTER_ID = "centerId";
    private static final String KEY_CLIENT_ID = "clientId";
    private static final String KEY_IS_SUPER_ADMIN = "isSuperAdmin";

    private final SsoUserMappingService ssoUserMappingService;
    private final SsoRoleBindingService ssoRoleBindingService;

    @Override
    public String messageType() {
        return SsoMessageTypes.SYNC_CLIENT_SUPER_ADMIN;
    }

    @Override
    public SaResult handle(SaSsoTemplate template, SaSsoMessage message) {
        Object centerIdObj = message.get(KEY_CENTER_ID);
        Object isSuperAdminObj = message.get(KEY_IS_SUPER_ADMIN);
        Object clientIdObj = message.get(KEY_CLIENT_ID);
        if (centerIdObj == null || isSuperAdminObj == null) {
            log.warn("[SSO] Server 推送超管同步消息缺少必要参数, centerId={}, isSuperAdmin={}, clientId={}",
                    centerIdObj, isSuperAdminObj, clientIdObj);
            return SaResult.error("centerId and isSuperAdmin are required");
        }
        Long localUserId;
        try {
            Object localUserIdObj = ssoUserMappingService.toClientUserId(centerIdObj);
            localUserId = localUserIdObj == null ? null : Long.valueOf(localUserIdObj.toString());
        } catch (Exception e) {
            log.warn("[SSO] Server 推送超管同步时，centerId 转本地用户失败. centerId={}, clientId={}", centerIdObj, clientIdObj, e);
            return SaResult.error("centerId convert failed");
        }
        if (localUserId == null) {
            log.warn("[SSO] Server 推送超管同步时，本地未找到用户映射. centerId={}, clientId={}", centerIdObj, clientIdObj);
            return SaResult.error("local user mapping not found");
        }
        boolean isSuperAdmin = Boolean.parseBoolean(isSuperAdminObj.toString());
        try {
            ssoRoleBindingService.applySuperAdmin(localUserId, isSuperAdmin);
            log.info("[SSO] Server 推送超管同步完成. centerId={}, localUserId={}, clientId={}, isSuperAdmin={}",
                    centerIdObj, localUserId, clientIdObj, isSuperAdmin);
            return SaResult.ok();
        } catch (Exception e) {
            log.warn("[SSO] Server 推送超管同步落库失败. centerId={}, localUserId={}, clientId={}, isSuperAdmin={}",
                    centerIdObj, localUserId, clientIdObj, isSuperAdmin, e);
            return SaResult.error("apply super admin failed");
        }
    }
}

