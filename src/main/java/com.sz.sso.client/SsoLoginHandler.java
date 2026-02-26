package com.sz.sso.client;

/**
 * SSO 登录处理器接口（SPI）.
 * <p>
 * 业务方需实现此接口，提供根据用户 ID 构建用户对象的能力。
 * starter 内部的 ticket 登录流程会调用此接口来获取完整的用户登录信息。
 * </p>
 * <p>
 * 类型参数 {@code U} 由业务方自行定义，对于 sz 框架通常是 {@code LoginUser}，
 * 对于其他框架可以是任意自定义用户对象。
 * </p>
 *
 * <p>接入示例（sz 框架）：</p>
 * <pre>{@code
 * @Component
 * public class SsoLoginHandlerImpl implements SsoLoginHandler<LoginUser> {
 *     @Override
 *     public LoginUser buildLoginUser(Long userId) {
 *         return sysUserService.buildLoginUser(userId);
 *     }
 * }
 * }</pre>
 *
 * @param <U> 用户对象类型，由业务方框架决定
 * @author sz
 * @version 2.0
 * @since 2025/6/20
 */
public interface SsoLoginHandler<U> {

    /**
     * 根据用户 ID 构建用户对象.
     * <p>
     * 此方法在 SSO ticket 验证通过后被调用，
     * 传入的 userId 是经过 {@link SsoUserMappingService#toClientUserId(Object)} 转换后的本地用户 ID。
     * </p>
     *
     * @param userId 本地用户 ID
     * @return 业务方框架所需的用户对象（含权限、角色等信息）
     */
    U buildLoginUser(Long userId);

}
