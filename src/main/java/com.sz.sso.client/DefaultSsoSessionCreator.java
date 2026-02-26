package com.sz.sso.client;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.sz.sso.client.pojo.SsoLoginResult;

/**
 * 基于 Sa-Token 的默认 {@link SsoSessionCreator} 实现.
 * <p>
 * 直接调用 {@link StpUtil#login(Object, SaLoginParameter)} 完成登录，
 * 然后读取 token 值与超时时间构建 {@link SsoLoginResult}。
 * </p>
 * <p>
 * 适用于所有基于 Sa-Token 的项目，接入方无需自行实现 {@link SsoSessionCreator}。
 * 若需自定义会话建立逻辑（例如将用户对象存入 TokenSession），
 * 只需注册自己的 {@link SsoSessionCreator} Bean，本实现将自动退出。
 * </p>
 *
 * @author sz
 * @version 1.0
 * @since 2025/6/20
 */
public class DefaultSsoSessionCreator implements SsoSessionCreator<Object> {

    @Override
    public SsoLoginResult createSession(Object user, SaLoginParameter parameter, Object loginId) {
        System.out.println("parameter ==" + parameter.toString());
        StpUtil.login(loginId, parameter);
        String accessToken = StpUtil.getTokenValue();
        long expireIn = StpUtil.getTokenTimeout();
        return SsoLoginResult.of(accessToken, expireIn, user);
    }

}
