package com.sz.ssoclient.spi;

/**
 * 宿主加载登录用户并建立本地 Session 的必需适配器。
 *
 * @param <U> 宿主登录用户类型
 */
public interface SsoClientLoginAdapter<U> {

    /** 按不透明本地用户 ID 加载宿主登录用户。 */
    U loadLoginUser(String localUserId);

    /** 使用可信上下文建立本地 Session，并返回访问令牌与精确撤销句柄。 */
    SsoClientLoginResult establishSession(U user, SsoClientLoginContext context);
}