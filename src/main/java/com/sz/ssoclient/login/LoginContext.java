package com.sz.ssoclient.login;

import cn.dev33.satoken.sso.model.SaCheckTicketResult;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.sz.ssoclient.pojo.SsoLoginResult;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * SSO 登录责任链上下文.
 */
@Getter
@Setter
@RequiredArgsConstructor
public class LoginContext<U> {

    private final SaCheckTicketResult checkTicketResult;

    private Long localUserId;

    private SaLoginParameter loginParameter;

    private boolean superAdmin;

    private U user;

    private SsoLoginResult result;
}

