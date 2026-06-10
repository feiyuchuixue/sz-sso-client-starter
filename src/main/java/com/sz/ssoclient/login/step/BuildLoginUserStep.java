package com.sz.ssoclient.login.step;

import com.sz.ssoclient.login.LoginContext;
import com.sz.ssoclient.login.LoginStep;
import com.sz.ssoclient.spi.SsoClientLoginAdapter;
import lombok.RequiredArgsConstructor;

/**
 * 从 Client 本地数据构建登录用户.
 */
@RequiredArgsConstructor
public class BuildLoginUserStep<U> implements LoginStep<U> {

    private final SsoClientLoginAdapter<U> loginAdapter;

    @Override
    public void execute(LoginContext<U> context) {
        context.setUser(loginAdapter.buildLoginUser(context.getLocalUserId()));
    }
}

