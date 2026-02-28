package com.sz.sso.client.service.impl;

import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.sso.model.SaCheckTicketResult;
import cn.dev33.satoken.sso.template.SaSsoClientUtil;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.sz.sso.client.SsoClientRoleProvider;
import com.sz.sso.client.SsoCoreConstant;
import com.sz.sso.client.SsoLoginHandler;
import com.sz.sso.client.SsoRoleBindingService;
import com.sz.sso.client.SsoSessionCreator;
import com.sz.sso.client.SsoUserMappingService;
import com.sz.sso.client.pojo.SsoLoginResult;
import com.sz.sso.client.service.SsoClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

/**
 * SSO Client 登录服务实现.
 * <p>
 * 处理通过 SSO ticket 验证后的本地登录逻辑，框架无关。
 * </p>
 *
 * <h3>登录流程</h3>
 * <ol>
 *   <li>（可选）首次登录默认角色初始化 — 仅当 {@link SsoClientRoleProvider} Bean 存在时触发，
 *       调用 {@link SsoRoleBindingService#applyDefaultRole} 写入 DB</li>
 *   <li>查询平台超管状态 — 向 Server 发送 {@code QUERY_USER_ROLES} 消息</li>
 *   <li>同步超管状态到本地 DB — 调用 {@link SsoRoleBindingService#applySuperAdmin}</li>
 *   <li>{@link SsoLoginHandler#buildLoginUser(Long)} — 从本地 DB 构建用户及角色信息
 *       （含前面步骤写入的默认角色和超管状态）</li>
 *   <li>{@link SsoSessionCreator#createSession} — 建立本地 Session</li>
 * </ol>
 *
 * <h3>超管同步</h3>
 * <p>
 * Client 内部超管角色变更（赋予/撤销）后，应调用
 * {@link com.sz.sso.client.SsoSyncHelper#syncSuperAdmin(Object, boolean)}
 * 异步通知 Server 更新 {@code sso_user_client_role} 表。
 * </p>
 *
 * @param <U> 用户对象类型，由业务方框架决定
 * @author sz
 * @version 3.0
 * @since 2025/6/23
 */
@Slf4j
@RequiredArgsConstructor
public class SsoClientServiceImpl<U> implements SsoClientService {

    private final SsoLoginHandler<U> ssoLoginHandler;
    private final SsoSessionCreator<U> ssoSessionCreator;
    private final SsoUserMappingService ssoUserMappingService;

    /** 可选：提供默认角色 key，存在时启用首次登录默认角色初始化 */
    @Nullable
    private final SsoClientRoleProvider ssoClientRoleProvider;

    /** 可选：默认实现或业务方自定义，执行首次登录角色写入和超管同步 */
    @Nullable
    private final SsoRoleBindingService ssoRoleBindingService;

    @Override
    @SuppressWarnings("unchecked")
    public SsoLoginResult login(SaCheckTicketResult ctr) {
        log.info("[SSO] ticket 登录开始, loginId={}, centerId={}, deviceId={}",
                 ctr.loginId, ctr.centerId, ctr.deviceId);

        cn.dev33.satoken.stp.parameter.SaLoginParameter parameter =
                new cn.dev33.satoken.stp.parameter.SaLoginParameter();
        parameter.setDeviceId(ctr.deviceId);
        parameter.setTimeout(ctr.remainTokenTimeout);
        parameter.setActiveTimeout(ctr.remainTokenTimeout);

        Long localUserId = Long.valueOf("" + ctr.loginId);

        // Step 1：首次登录默认角色初始化（可选，仅写 DB）
        if (ssoClientRoleProvider != null && ssoRoleBindingService != null) {
            applyDefaultRoleIfNeeded(localUserId);
        }

        // Step 2：查询平台超管状态（失败降级为 false）
        boolean isSuperAdmin = queryIsSuperAdmin(localUserId);

        // Step 3：同步超管状态到本地 DB（每次登录都执行，确保与平台一致）
        if (ssoRoleBindingService != null) {
            applySuperAdminIfNeeded(localUserId, isSuperAdmin);
        }

        // Step 4：从本地 DB 构建用户（含角色、权限、部门等完整信息）
        //         此时 DB 已包含 Step1 写入的默认角色 + Step3 同步的超管状态
        U user = ssoLoginHandler.buildLoginUser(localUserId);

        // Step 5：建立 Session
        SsoLoginResult result = ssoSessionCreator.createSession(user, parameter, ctr.loginId);

        // 将超管状态存入 TokenSession（供 SsoClientUtil.isSuperAdmin() 读取）
        StpUtil.getTokenSessionByToken(result.getAccessToken())
               .set(SsoCoreConstant.SESSION_KEY_IS_SUPER_ADMIN, isSuperAdmin);

        log.info("[SSO] ticket 登录成功, localUserId={}, isSuperAdmin={}", localUserId, isSuperAdmin);
        return result;
    }

    /**
     * 向 SSO Server 查询用户在本 Client 中的超管状态.
     * <p>
     * 失败时（Server 不可达、返回异常等）降级为 {@code false}，不中断登录流程。
     * </p>
     */
    private boolean queryIsSuperAdmin(Long localUserId) {
        try {
            Object centerId = ssoUserMappingService.toServerUserId(localUserId);
            String clientId = SaSsoClientUtil.getSsoTemplate().getClient();

            SaSsoMessage message = new SaSsoMessage();
            message.setType(SsoCoreConstant.MESSAGE_QUERY_USER_ROLES);
            message.set("centerId", centerId);
            message.set("clientId", clientId);

            log.debug("[SSO] 查询超管状态: localUserId={}, centerId={}, clientId={}",
                      localUserId, centerId, clientId);

            SaResult result = SaSsoClientUtil.pushMessageAsSaResult(message);

            if (result == null || result.getCode() != 200 || result.getData() == null) {
                log.warn("[SSO] 查询超管状态失败，降级为 false: localUserId={}, result={}",
                         localUserId, result);
                return false;
            }

            Object data = result.getData();
            boolean isSuperAdmin = Boolean.TRUE.equals(data);
            log.debug("[SSO] 查询超管状态完成: localUserId={}, isSuperAdmin={}", localUserId, isSuperAdmin);
            return isSuperAdmin;

        } catch (Exception e) {
            log.warn("[SSO] 查询超管状态异常，降级为 false: localUserId={}, error={}",
                     localUserId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 首次登录默认角色初始化（仅写 DB）.
     */
    private void applyDefaultRoleIfNeeded(Long localUserId) {
        try {
            String defaultRoleKey = ssoClientRoleProvider.getDefaultRoleKey();
            ssoRoleBindingService.applyDefaultRole(localUserId, defaultRoleKey);
        } catch (Exception e) {
            log.warn("[SSO] 默认角色初始化异常，跳过: localUserId={}, error={}",
                     localUserId, e.getMessage(), e);
        }
    }

    /**
     * 同步超管状态到本地 DB.
     */
    private void applySuperAdminIfNeeded(Long localUserId, boolean isSuperAdmin) {
        try {
            ssoRoleBindingService.applySuperAdmin(localUserId, isSuperAdmin);
        } catch (Exception e) {
            log.warn("[SSO] 超管状态同步异常，跳过: localUserId={}, isSuperAdmin={}, error={}",
                     localUserId, isSuperAdmin, e.getMessage(), e);
        }
    }

}
