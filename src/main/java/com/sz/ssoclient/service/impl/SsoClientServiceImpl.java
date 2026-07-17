package com.sz.ssoclient.service.impl;

import cn.dev33.satoken.sso.model.SaCheckTicketResult;
import com.sz.ssoclient.login.SsoLoginCommand;
import com.sz.ssoclient.login.SsoLoginOrchestrator;
import com.sz.ssoclient.pojo.SsoLoginResult;
import com.sz.ssoclient.service.SsoClientService;
import lombok.extern.slf4j.Slf4j;

/**
 * SSO Client 登录服务实现.
 */
@Slf4j
public class SsoClientServiceImpl<U> implements SsoClientService {

    private final SsoLoginOrchestrator<U> loginOrchestrator;

    public SsoClientServiceImpl(SsoLoginOrchestrator<U> loginOrchestrator) {
        this.loginOrchestrator = loginOrchestrator;
    }

    @Override
    public SsoLoginResult login(SaCheckTicketResult ctr) {
        log.info("[SSO] ticket 登录开始, loginId={}, centerId={}, deviceId={}", ctr.loginId, ctr.centerId, ctr.deviceId);
        SsoLoginResult result = loginOrchestrator.login(ctr);
        log.info("[SSO] ticket 登录成功, loginId={}", ctr.loginId);
        return result;
    }

    @Override
    public SsoLoginResult login(SsoLoginCommand command) {
        return loginOrchestrator.login(command);
    }
}