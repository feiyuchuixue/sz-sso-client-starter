package com.sz.sso.client;

import com.sz.sso.client.pojo.SsoUserContext;

/**
 * SSO 角色绑定服务接口（SPI）.
 * <p>
 * 业务方可选实现此接口，决定是否及如何根据平台下发的角色提议初始化本地用户权限。
 * 若业务方未提供实现，Starter 将使用内置的
 * {@link DefaultSsoRoleBindingService} 作为默认实现（仅打印 warn 日志）。
 * </p>
 * <p>
 * 典型使用场景：用户首次登录 Client 时，根据平台提议的 {@code ssoRoleKey}
 * 在本地 DB 中初始化用户角色记录。
 * </p>
 *
 * <p>接入示例（sz 框架）：</p>
 * <pre>{@code
 * @Service
 * public class SsoRoleBindingServiceImpl implements SsoRoleBindingService {
 *     @Override
 *     public void applyRoles(Long localUserId, SsoUserContext ssoContext, LoginUser loginUser) {
 *         if (!loginUser.getRoles().isEmpty()) {
 *             return; // 非首次登录，尊重本地权限体系
 *         }
 *         // 首次登录：按平台提议写入本地角色
 *         sysUserRoleService.assignRole(localUserId, ssoContext.getSsoRoleKey());
 *         loginUser.getRoles().add(ssoContext.getSsoRoleKey());
 *     }
 * }
 * }</pre>
 *
 * @author sz
 * @version 1.0
 * @since 2025/6/23
 */
public interface SsoRoleBindingService {

    /**
     * 处理平台下发的角色提议.
     * <p>
     * 在 {@code buildLoginUser()} 之后、{@code createSession()} 之前调用。
     * 方法内可修改 {@code loginUser} 对象（如添加角色），修改立即在本次 Session 中生效。
     * </p>
     *
     * @param localUserId Client 本地用户 ID
     * @param ssoContext  平台下发的用户上下文（含 isSuperAdmin、ssoRoleKey）
     * @param loginUser   当前登录用户对象（由 SsoLoginHandler.buildLoginUser 构建）
     */
    void applyRoles(Long localUserId, SsoUserContext ssoContext, Object loginUser);

}
