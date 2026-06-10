package com.sz.ssoclient.spi;

/**
 * SSO 登录角色初始化 SPI.
 */
public interface SsoRoleBindingService {

    void applyDefaultRole(Long localUserId, String defaultRoleKey);

    void applySuperAdmin(Long localUserId, boolean isSuperAdmin);
}

