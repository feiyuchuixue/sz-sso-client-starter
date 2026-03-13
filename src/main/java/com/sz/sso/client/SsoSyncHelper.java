package com.sz.sso.client;

import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.sso.template.SaSsoClientUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;

/**
 * SSO 超管状态同步工具（Spring Bean）.
 * <p>
 * Client 内部超管角色发生变更（赋予或撤销）时，调用此类将变更同步到 SSO Server，
 * 确保 Server 的 {@code sso_user_client_role} 表保持与 Client 一致。
 * </p>
 * <p>
 * 同步为<b>异步执行</b>，失败时仅打印 warn 日志，不影响 Client 本地操作的结果。
 * Server 端始终是超管状态的权威来源，Client 只负责触发同步。
 * </p>
 *
 * <p>接入示例：</p>
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class SysUserRoleServiceImpl implements SysUserRoleService {
 *
 *     private final SsoSyncHelper ssoSyncHelper;
 *
 *     public void assignSuperAdmin(Long localUserId) {
 *         // ... 本地 DB 操作 ...
 *         sysUserRoleMapper.insert(superAdminRole);
 *         // 同步到 SSO Server（异步，失败忽略）
 *         ssoSyncHelper.syncSuperAdmin(localUserId, true);
 *     }
 *
 *     public void revokeSuperAdmin(Long localUserId) {
 *         // ... 本地 DB 操作 ...
 *         sysUserRoleMapper.delete(superAdminRole);
 *         // 同步到 SSO Server（异步，失败忽略）
 *         ssoSyncHelper.syncSuperAdmin(localUserId, false);
 *     }
 * }
 * }</pre>
 *
 * @author sz
 * @version 1.0
 * @since 2025/6/23
 */
@Slf4j
@RequiredArgsConstructor
public class SsoSyncHelper {

    /**
     * 异步通知 SSO Server 同步当前 Client 某用户的超管状态. 当 Client 本地用户的超管角色发生变更时，需要调用此方法将变更同步到 SSO Server。
     * <p>
     * 内部通过 {@link SsoUserMappingService#toServerUserId(Object)} 将本地用户 ID
     * 转换为 centerId，再通过 SSO 消息通道发送 {@code SYNC_SUPER_ADMIN} 消息到 Server。
     * </p>
     *
     * @param centerId     ssoUserMappingService.toServerUserId(localUserId) 的结果，即 SSO Server 的用户 ID
     * @param isSuperAdmin {@code true} 表示赋予超管；{@code false} 表示撤销超管
     */
    @Async
    public void syncSuperAdmin(Object centerId, boolean isSuperAdmin) {
        try {
            String clientId = SaSsoClientUtil.getSsoTemplate().getClient();

            SaSsoMessage message = new SaSsoMessage();
            message.setType(SsoCoreConstant.MESSAGE_SYNC_SUPER_ADMIN);
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
