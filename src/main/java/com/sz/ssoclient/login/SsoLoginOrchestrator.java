package com.sz.ssoclient.login;

import cn.dev33.satoken.sso.model.SaCheckTicketResult;
import com.sz.ssoclient.pojo.SsoLoginResult;

import java.util.List;

/**
 * SSO 登录编排器.
 */
public class SsoLoginOrchestrator<U> {

    private final List<LoginStep<U>> steps;

    public SsoLoginOrchestrator(List<LoginStep<U>> steps) {
        this.steps = List.copyOf(steps);
    }

    public SsoLoginResult login(SaCheckTicketResult checkTicketResult) {
        LoginContext<U> context = new LoginContext<>(checkTicketResult);
        for (LoginStep<U> step : steps) {
            step.execute(context);
        }
        return context.getResult();
    }
}

