package com.sz.ssoclient.clientaccess.web;

import cn.dev33.satoken.sso.processor.SaSsoClientProcessor;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.parameter.SaLogoutParameter;

import java.time.Instant;

/** Sa-Token implementation of the Client-local session boundary. */
public class SaTokenClientLocalSessionAccessor implements ClientLocalSessionAccessor {

    @Override
    public ClientLocalSession current() {
        StpLogic logic = logic();
        if (!logic.isLogin()) {
            return ClientLocalSession.anonymous();
        }
        long timeoutSeconds = logic.getTokenTimeout();
        Instant expireAt = timeoutSeconds > 0 ? Instant.now().plusSeconds(timeoutSeconds) : null;
        return new ClientLocalSession(true, logic.getLoginId(), logic.getLoginDeviceId(), expireAt);
    }

    @Override
    public void logoutCurrentSession() {
        StpLogic logic = logic();
        if (logic.isLogin()) {
            logic.getTokenSession().logout();
            logic.logout();
        }
    }

    @Override
    public void logoutCurrentDevice(Object localUserId, String deviceId) {
        SaLogoutParameter parameter = logic().createSaLogoutParameter();
        parameter.setDeviceId(deviceId);
        logic().logout(localUserId, parameter);
    }

    @Override
    public void logoutAccount(Object localUserId) {
        logic().logout(localUserId);
    }

    private static StpLogic logic() {
        return SaSsoClientProcessor.instance.ssoClientTemplate.getStpLogicOrGlobal();
    }
}
