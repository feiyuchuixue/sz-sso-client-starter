package com.sz.ssoclient.login;

/**
 * SSO 登录步骤.
 */
public interface LoginStep<U> {

    void execute(LoginContext<U> context);
}

