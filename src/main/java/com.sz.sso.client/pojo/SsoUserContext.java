package com.sz.sso.client.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 平台下发的用户上下文.
 * <p>
 * 由 SSO Server 的 {@code QUERY_USER_ROLES} 消息返回，存入当前用户的 TokenSession，
 * key 为 {@link com.sz.sso.client.SsoCoreConstant#SESSION_KEY_SSO_CONTEXT}。
 * </p>
 * <ul>
 *   <li>{@code isSuperAdmin=true}  → {@code ssoRoleKey} 对应本地超管角色 key</li>
 *   <li>{@code isSuperAdmin=false} → {@code ssoRoleKey} 对应本地默认角色 key</li>
 * </ul>
 *
 * @author sz
 * @version 1.0
 * @since 2025/6/23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SsoUserContext {

    /**
     * SSO Server 侧用户 ID（centerId）
     */
    private String centerId;

    /**
     * 平台是否认定该用户为本 Client 的超管
     */
    private Boolean isSuperAdmin;

    /**
     * 映射后的本地角色 key.
     * <ul>
     *   <li>isSuperAdmin=true  → SsoClientRoleProvider.getSuperAdminRoleKey()</li>
     *   <li>isSuperAdmin=false → SsoClientRoleProvider.getDefaultRoleKey()</li>
     * </ul>
     */
    private String ssoRoleKey;

}
