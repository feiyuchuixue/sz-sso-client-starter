package com.sz.ssoclient.login.step;

import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.sz.ssoclient.login.LoginContext;
import com.sz.ssoclient.login.LoginStep;

/**
 * 解析 ticket 校验结果并准备本地登录参数.
 */
public class PrepareLoginParameterStep<U> implements LoginStep<U> {

    @Override
    public void execute(LoginContext<U> context) {
        SaLoginParameter parameter = new SaLoginParameter();
        if (context.getLoginCommand() != null) {
            parameter.setDeviceId(context.getLoginCommand().deviceId());
            parameter.setTimeout(context.getLoginCommand().sessionTimeoutSeconds());
            parameter.setActiveTimeout(context.getLoginCommand().sessionTimeoutSeconds());
            context.setLocalUserId(context.getLoginCommand().localUserId());
        } else {
            parameter.setDeviceId(context.getCheckTicketResult().deviceId);
            parameter.setTimeout(context.getCheckTicketResult().remainTokenTimeout);
            parameter.setActiveTimeout(context.getCheckTicketResult().remainTokenTimeout);
            context.setLocalUserId(Long.valueOf(String.valueOf(context.getCheckTicketResult().loginId)));
        }
        context.setLoginParameter(parameter);
    }
}

