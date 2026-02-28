package com.sz.sso.client;

/**
 * SSO 登录角色初始化接口（SPI）.
 * <p>
 * 业务方可选实现此接口，决定 SSO 登录 Client 时如何初始化本地角色和同步超管状态。
 * 若业务方未提供实现，Starter 将使用内置的 {@link DefaultSsoRoleBindingService}
 * 作为默认实现（仅打印 warn 日志）。
 * </p>
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>{@link #applyDefaultRole} — 首次登录默认角色初始化（用户无角色时写入默认角色）</li>
 *   <li>{@link #applySuperAdmin} — 根据平台超管状态同步本地超管角色/标记</li>
 * </ul>
 *
 * <h3>调用时机</h3>
 * <p>
 * 两个方法均在 {@code buildLoginUser()} <b>之前</b>调用，实现方只需操作 DB，
 * 后续 {@code buildLoginUser()} 从 DB 读取最新数据即可。
 * </p>
 *
 * <h3>调用顺序</h3>
 * <pre>
 *   applyDefaultRole(userId, defaultRoleKey)   ← 写入默认角色到 DB（首次登录时）
 *   isSuperAdmin = queryIsSuperAdmin(userId)   ← 从 Server 获取超管状态
 *   applySuperAdmin(userId, isSuperAdmin)      ← 同步超管状态到本地 DB
 *   buildLoginUser(userId)                     ← 从 DB 构建完整用户信息
 *   createSession(user, ...)                   ← 存入 Redis
 * </pre>
 *
 * @author sz
 * @version 3.0
 * @since 2025/6/23
 */
public interface SsoRoleBindingService {

    /**
     * 处理用户首次 SSO 登录时的默认角色初始化.
     * <p>
     * 在 {@code buildLoginUser()} 之前调用。
     * 实现方应检查用户在本地是否已有角色，若无角色则写入默认角色到 DB。
     * 无需操作内存中的用户对象——后续 {@code buildLoginUser()} 会从 DB 读取最新数据。
     * </p>
     *
     * @param localUserId    Client 本地用户 ID
     * @param defaultRoleKey 默认角色 key（来自 {@link SsoClientRoleProvider#getDefaultRoleKey()}）
     */
    void applyDefaultRole(Long localUserId, String defaultRoleKey);

    /**
     * 根据平台超管状态同步本地超管角色/标记.
     * <p>
     * 在 {@code applyDefaultRole()} 之后、{@code buildLoginUser()} 之前调用。
     * 每次 SSO 登录都会调用，实现方应根据 {@code isSuperAdmin} 更新本地 DB 中的
     * 超管状态（如角色绑定、用户标记字段等），确保与平台一致。
     * </p>
     * <p>
     * 典型实现（sz 框架）：
     * <ul>
     *   <li>{@code isSuperAdmin=true} → 设置 {@code sys_user.user_tag_cd="1001002"}，
     *       写入超管角色到 {@code sys_user_role}</li>
     *   <li>{@code isSuperAdmin=false} → 设置 {@code sys_user.user_tag_cd="1001003"}，
     *       移除超管角色</li>
     * </ul>
     * </p>
     *
     * @param localUserId  Client 本地用户 ID
     * @param isSuperAdmin 平台认定的超管状态（{@code true}=超管，{@code false}=非超管）
     */
    void applySuperAdmin(Long localUserId, boolean isSuperAdmin);

}
