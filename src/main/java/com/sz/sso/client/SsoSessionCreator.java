package com.sz.sso.client;

import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.sz.sso.client.pojo.SsoLoginResult;

/**
 * SSO 会话创建器接口（SPI）.
 * <p>
 * 业务方（或框架适配层）需实现此接口，负责在 SSO ticket 验证通过并获得用户对象后，
 * 建立本地会话（如生成 token、写入 session 等），并返回登录结果。
 * </p>
 * <p>
 * 类型参数 {@code U} 必须与配套的 {@link SsoLoginHandler} 的类型参数保持一致。
 * 对于 sz 框架，此接口由 {@code sz-sso-client-adapter} 模块的 {@code SzSsoSessionCreator} 自动实现，
 * 无需业务方手动注册。
 * </p>
 *
 * <p>接入示例（自定义框架）：</p>
 * <pre>{@code
 * @Component
 * public class MySessionCreator implements SsoSessionCreator<MyUser> {
 *     @Override
 *     public SsoLoginResult createSession(MyUser user, SaLoginParameter parameter, Object loginId) {
 *         String token = myAuthService.login(loginId, parameter);
 *         return SsoLoginResult.of(token, 86400L, user);
 *     }
 * }
 * }</pre>
 *
 * @param <U> 用户对象类型，与 {@link SsoLoginHandler} 的类型参数一致
 * @author sz
 * @version 2.0
 * @since 2025/6/20
 */
public interface SsoSessionCreator<U> {

    /**
     * 建立本地会话并返回登录结果.
     * <p>
     * 在 SSO ticket 校验完成、用户对象构建完成后调用。
     * 实现方负责调用本框架的登录方法（生成 token、写入 session 等），
     * 并将结果封装为 {@link SsoLoginResult} 返回。
     * </p>
     *
     * @param user      由 {@link SsoLoginHandler#buildLoginUser(Long)} 返回的用户对象
     * @param parameter Sa-Token 登录参数（含 deviceType、deviceId、timeout 等）
     * @param loginId   本地用户 ID（已经过 toClientUserId 转换）
     * @return 登录结果（accessToken、expireIn、userInfo）
     */
    SsoLoginResult createSession(U user, SaLoginParameter parameter, Object loginId);

}
