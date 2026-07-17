package com.sz.ssoclient.login.step;

import com.sz.ssoclient.login.LoginContext;
import com.sz.ssoclient.login.LoginStep;
import com.sz.ssoclient.spi.SsoClientLoginAdapter;
import lombok.RequiredArgsConstructor;

/**
 * 建立 Client 本地登录态并组装响应.
 */
@RequiredArgsConstructor
public class CreateLoginResultStep<U> implements LoginStep<U> {

    private final SsoClientLoginAdapter<U> loginAdapter;

    @Override
    public void execute(LoginContext<U> context) {
        context.setResult(loginAdapter.createLoginResult(
                context.getUser(),
                context.getLoginParameter(),
                context.getLocalUserId()));
    }
}

