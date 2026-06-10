package com.sz.ssoclient.spi;

import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.sz.ssoclient.pojo.SsoLoginResult;

/**
 * SSO Client 登录适配器 SPI.
 *
 * @param <U> 用户对象类型，由业务方框架决定
 */
public interface SsoClientLoginAdapter<U> {

    U buildLoginUser(Long userId);

    SsoLoginResult createLoginResult(U user, SaLoginParameter parameter, Object loginId);
}

