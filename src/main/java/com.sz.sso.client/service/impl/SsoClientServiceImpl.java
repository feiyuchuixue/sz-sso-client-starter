package com.sz.sso.client.service.impl;

import cn.dev33.satoken.sso.model.SaCheckTicketResult;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.sz.sso.client.SsoLoginHandler;
import com.sz.sso.client.SsoSessionCreator;
import com.sz.sso.client.pojo.SsoLoginResult;
import com.sz.sso.client.service.SsoClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SSO Client 登录服务实现.
 * <p>
 * 处理通过 SSO ticket 验证后的本地登录逻辑，框架无关。
 * 通过 {@link SsoLoginHandler} SPI 获取用户对象，
 * 通过 {@link SsoSessionCreator} SPI 建立本地会话。
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
    public SsoLoginResult login(SaCheckTicketResult ctr) {
        log.info("SSO ticket 登录开始, loginId={}, centerId={}, deviceID={}", ctr.loginId, ctr.centerId, ctr.deviceId);

        SaLoginParameter parameter = new SaLoginParameter();
        parameter.setDeviceId(ctr.deviceId);
        parameter.setTimeout(ctr.remainTokenTimeout);
        parameter.setActiveTimeout(ctr.remainTokenTimeout);

        Object loginId = ctr.loginId;
        U user = ssoLoginHandler.buildLoginUser(Long.valueOf("" + loginId));

        SsoLoginResult result = ssoSessionCreator.createSession(user, parameter, loginId);

        log.info("SSO ticket 登录成功, loginId={}", loginId);
        return result;
    }

}
