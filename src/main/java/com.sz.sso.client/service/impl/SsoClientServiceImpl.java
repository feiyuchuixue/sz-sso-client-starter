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
import com.sz.sso.client.pojo.SsoUserContext;
import com.sz.sso.client.service.SsoClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

/**
 * SSO Client 登录服务实现.
 * <p>
 * 处理通过 SSO ticket 验证后的本地登录逻辑，框架无关。
 * 通过 {@link SsoLoginHandler} SPI 获取用户对象，
 * 通过 {@link SsoSessionCreator} SPI 建立本地会话。
 * </p>
 * <p>
 * 若业务方提供了 {@link SsoClientRoleProvider} Bean，则在 buildLoginUser 之后、
 * createSession 之前额外执行角色下发流程：向 SSO Server 查询用户是否为超管，
 * 并调用 {@link SsoRoleBindingService} 完成本地角色初始化。
 * </p>
 * <p>
 * 类型参数 {@code U} 由业务方框架决定，两个 SPI 实现的类型参数必须一致。
 * </p>
 *
 * @param <U> 用户对象类型
 * @author sz
 * @version 2.0
 * @since 2025/6/23
 */
@Slf4j
@RequiredArgsConstructor
public class SsoClientServiceImpl<U> implements SsoClientService {

    private final SsoLoginHandler<U> ssoLoginHandler;
    private final SsoSessionCreator<U> ssoSessionCreator;
    private final SsoUserMappingService ssoUserMappingService;

    /** 可选：业务方提供则启用角色下发流程，否则跳过 */
    @Nullable
    private final SsoClientRoleProvider ssoClientRoleProvider;

    /** 可选：DefaultSsoRoleBindingService 或业务方自定义实现 */
    @Nullable
    private final SsoRoleBindingService ssoRoleBindingService;

    /**
     * SSO ticket 登录.
     * <p>
     * 此时 ctr.loginId 已是转换后的 Client 端本地用户 ID（由 toClientUserId 完成）。
     * </p>
     *
     * @param ctr SaCheckTicketResult
     * @return SsoLoginResult
     */
    @Override
    @SuppressWarnings("unchecked")
    public SsoLoginResult login(SaCheckTicketResult ctr) {
        log.info("SSO ticket 登录开始, loginId={}, centerId={}, deviceID={}", ctr.loginId, ctr.centerId, ctr.deviceId);

        cn.dev33.satoken.stp.parameter.SaLoginParameter parameter = new cn.dev33.satoken.stp.parameter.SaLoginParameter();
        parameter.setDeviceId(ctr.deviceId);
        parameter.setTimeout(ctr.remainTokenTimeout);
        parameter.setActiveTimeout(ctr.remainTokenTimeout);

        Long loginId = Long.valueOf("" + ctr.loginId);
        U user = ssoLoginHandler.buildLoginUser(loginId);

        // Step 3 & 4：角色下发流程（SsoClientRoleProvider 不存在时跳过）
        SsoUserContext ssoContext = null;
        if (ssoClientRoleProvider != null && ssoRoleBindingService != null) {
            ssoContext = queryAndBuildSsoContext(loginId, ctr);
            if (ssoContext != null) {
                ssoRoleBindingService.applyRoles(loginId, ssoContext, user);
            }
        }

        // Step 5：建立 Session
        SsoLoginResult result = ssoSessionCreator.createSession(user, parameter, ctr.loginId);

        // 将 ssoContext 存入 TokenSession（非 null 时）
        if (ssoContext != null) {
            StpUtil.getTokenSessionByToken(result.getAccessToken())
                   .set(SsoCoreConstant.SESSION_KEY_SSO_CONTEXT, ssoContext);
            log.info("SSO ticket 登录: ssoContext 已存入 TokenSession, isSuperAdmin={}, ssoRoleKey={}",
                     ssoContext.getIsSuperAdmin(), ssoContext.getSsoRoleKey());
        }

        log.info("SSO ticket 登录成功, loginId={}", loginId);
        return result;
    }

    /**
     * 向 SSO Server 查询用户角色信息并构建 {@link SsoUserContext}.
     *
     * @param localUserId 本地用户 ID
     * @param ctr         ticket 验证结果（含 centerId、clientId）
     * @return SsoUserContext，失败时返回 null（不中断登录流程）
     */
    @Nullable
    private SsoUserContext queryAndBuildSsoContext(Long localUserId, SaCheckTicketResult ctr) {
        try {
            Object centerId = ssoUserMappingService.toServerUserId(localUserId);
            String clientId = SaSsoClientUtil.getSsoTemplate().getClient();

            SaSsoMessage message = new SaSsoMessage();
            message.setType(SsoCoreConstant.MESSAGE_QUERY_USER_ROLES);
            message.set("centerId", centerId);
            message.set("clientId", clientId);

            log.info("SSO 角色下发: 向 Server 查询角色, localUserId={}, centerId={}, clientId={}",
                     localUserId, centerId, clientId);

            SaResult result = SaSsoClientUtil.pushMessageAsSaResult(message);

            if (result == null) {
                log.warn("SSO 角色下发: Server 返回为空, 跳过角色下发");
                return null;
            }

            Object data = result.getData();
            if (data == null || result.getCode() != 200) {
                log.warn(
                        "SSO 角色下发: Server 返回失败, code={}, msg={}, 跳过角色下发, data={}",
                        result.getCode(),
                        result.getMsg(),
                        data
                );
                return null;
            }

            Boolean isSuperAdmin = (data instanceof Boolean) ? (Boolean) data : null;
            if (isSuperAdmin == null) {
                log.warn("SSO 角色下发: Server 响应中缺少 isSuperAdmin 字段，跳过角色下发");
                return null;
            }

            String ssoRoleKey = isSuperAdmin
                    ? ssoClientRoleProvider.getSuperAdminRoleKey()
                    : ssoClientRoleProvider.getDefaultRoleKey();

            return SsoUserContext.builder()
                    .centerId(String.valueOf(centerId))
                    .isSuperAdmin(isSuperAdmin)
                    .ssoRoleKey(ssoRoleKey)
                    .build();

        } catch (Exception e) {
            log.error("SSO 角色下发: 查询异常, localUserId={}, 跳过角色下发, error={}",
                      localUserId, e.getMessage(), e);
            return null;
        }
    }

}
