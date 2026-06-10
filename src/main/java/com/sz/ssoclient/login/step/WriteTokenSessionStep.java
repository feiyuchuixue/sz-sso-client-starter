package com.sz.ssoclient.login.step;

import cn.dev33.satoken.stp.StpUtil;
import com.sz.ssoclient.login.LoginContext;
import com.sz.ssoclient.login.LoginStep;
import com.sz.ssocore.SsoProtocolFields;
import lombok.extern.slf4j.Slf4j;

/**
 * 将平台超管状态写入当前 TokenSession.
 */
@Slf4j
public class WriteTokenSessionStep<U> implements LoginStep<U> {

    @Override
    public void execute(LoginContext<U> context) {
        if (context.getResult() == null || context.getResult().getAccessToken() == null) {
            return;
        }
        try {
            StpUtil.getTokenSessionByToken(context.getResult().getAccessToken())
                    .set(SsoProtocolFields.IS_SUPER_ADMIN, context.isSuperAdmin());
        } catch (Exception e) {
            log.warn("[SSO] 写入 isSuperAdmin 到 TokenSession 异常，跳过: token={}, error={}",
                    context.getResult().getAccessToken(), e.getMessage(), e);
        }
    }
}

