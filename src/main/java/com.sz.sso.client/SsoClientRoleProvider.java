package com.sz.sso.client;

/**
 * Client 本地默认角色 key 提供者接口（SPI）.
 * <p>
 * 业务方可选实现此接口，告知 Starter 新用户首次 SSO 登录时应赋予的默认角色 key。
 * 若业务方未提供此接口的实现 Bean，首次登录的默认角色初始化步骤将跳过。
 * </p>
 * <p>
 * 注意：超管角色的赋予与撤销由 Client 业务代码自主管理，变更后通过
 * {@link SsoSyncHelper#syncSuperAdmin(Object, boolean)} 通知 Server 同步，
 * 无需在此处声明超管角色 key。
 * </p>
 *
 * <p>接入示例：</p>
 * <pre>{@code
 * @Component
 * public class MyRoleProvider implements SsoClientRoleProvider {
 *     @Override
 *     public String getDefaultRoleKey() {
 *         return "user";
 *     }
 * }
 * }</pre>
 *
 * @author sz
 * @version 2.0
 * @since 2025/6/23
 */
public interface SsoClientRoleProvider {

    /**
     * 返回新用户首次 SSO 登录时应赋予的默认角色 key.
     * <p>
     * 仅在用户本地无任何角色记录时触发（即 {@code loginUser.getRoles()} 为空）。
     * 之后的角色管理完全由 Client 内部权限体系负责。
     * </p>
     * <p>例如 {@code "user"}</p>
     *
     * @return 默认角色 key
     */
    String getDefaultRoleKey();

}
