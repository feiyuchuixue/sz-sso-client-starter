package com.sz.sso.client;

import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.sz.sso.client.pojo.SsoLoginResult;

/**
 * SSO Client 登录适配器接口（SPI）.
 * <p>
 * 业务方需实现此接口，完成本地用户对象构建与本地会话创建。
 * Starter 在完成角色初始化、超管状态同步后，调用此接口生成最终登录结果。
 * </p>
 *
 * @param <U> 用户对象类型，由业务方框架决定
 * @author sz
 * @version 1.0
 * @since 2026/5/20
 */
public interface SsoClientLoginAdapter<U> {

    /**
     * 根据用户 ID 构建用户对象.
     *
     * @param userId 本地用户 ID
     * @return 业务方框架所需的用户对象（含权限、角色等信息）
     */
    U buildLoginUser(Long userId);

    /**
     * 建立本地会话并返回登录结果.
     *
     * @param user      由 {@link #buildLoginUser(Long)} 返回的用户对象
     * @param parameter Sa-Token 登录参数（含 deviceId、timeout 等）
     * @param loginId   本地用户 ID（已经过 toClientUserId 转换）
     * @return 登录结果（accessToken、expireIn、userInfo）
     */
    SsoLoginResult createLoginResult(U user, SaLoginParameter parameter, Object loginId);
}
