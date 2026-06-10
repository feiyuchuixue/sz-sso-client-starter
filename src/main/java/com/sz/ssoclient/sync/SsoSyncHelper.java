package com.sz.ssoclient.sync;

import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.sso.template.SaSsoClientUtil;
import com.sz.ssocore.SsoMessageTypes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;

/**
 * SSO 超管状态同步工具.
 */
@Slf4j
public class SsoSyncHelper {

    @Async
    public void syncSuperAdmin(Object centerId, boolean isSuperAdmin) {
        try {
            String clientId = SaSsoClientUtil.getSsoTemplate().getClient();
            SaSsoMessage message = new SaSsoMessage();
            message.setType(SsoMessageTypes.SYNC_SUPER_ADMIN);
            message.set("centerId", centerId);
            message.set("clientId", clientId);
            message.set("isSuperAdmin", isSuperAdmin);
            log.info("[SSO] 同步超管状态: centerId={}, clientId={}, isSuperAdmin={}", centerId, clientId, isSuperAdmin);
            SaSsoClientUtil.pushMessage(message);
            log.info("[SSO] 同步超管状态完成: centerId={}, isSuperAdmin={}", centerId, isSuperAdmin);
        } catch (Exception e) {
            log.warn("[SSO] 同步超管状态失败, centerId={}, isSuperAdmin={}, error={}", centerId, isSuperAdmin, e.getMessage(), e);
        }
    }
}

