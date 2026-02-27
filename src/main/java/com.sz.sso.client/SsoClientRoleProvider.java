package com.sz.sso.client;

/**
 * Client 本地角色 key 提供者接口（SPI）.
 * <p>
 * 业务方可选实现此接口，告知 Starter 本地超管角色和默认角色的 key。
 * 若业务方未提供此接口的实现 Bean，整个角色下发流程将跳过，不影响原有登录流程。
 * </p>
 *
 * <p>接入示例：</p>
 * <pre>{@code
 * @Component
 * public class MyRoleProvider implements SsoClientRoleProvider {
 *     @Override
 *     public String getSuperAdminRoleKey() {
 *         return "admin";
 *     }
 *     @Override
 *     public String getDefaultRoleKey() {
 *         return "user";
 *     }
 * }
 * }</pre>
 *
 * @author sz
 * @version 1.0
 * @since 2025/6/23
 */
public interface SsoClientRoleProvider {

    /**
     * 返回本地超管角色的 key.
     * <p>例如 {@code "admin"}</p>
     *
     * @return 超管角色 key
     */
    String getSuperAdminRoleKey();

    /**
     * 返回本地默认角色的 key.
     * <p>例如 {@code "user"}</p>
     *
     * @return 默认角色 key
     */
    String getDefaultRoleKey();

}
