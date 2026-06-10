package com.sz.ssoclient.sync;

import com.sz.ssoclient.spi.SsoRoleBindingService;
import lombok.extern.slf4j.Slf4j;

/**
 * SsoRoleBindingService 的内置默认实现，仅记录提示日志.
 */
@Slf4j
public class DefaultSsoRoleBindingService implements SsoRoleBindingService {

    @Override
    public void applyDefaultRole(Long localUserId, String defaultRoleKey) {
        log.warn("[SSO] 用户 {} 首次登录，平台建议默认角色 key={}，但未找到 SsoRoleBindingService 实现，跳过默认角色初始化。",
                localUserId, defaultRoleKey);
    }

    @Override
    public void applySuperAdmin(Long localUserId, boolean isSuperAdmin) {
        log.warn("[SSO] 用户 {} 超管状态={}，但未找到 SsoRoleBindingService 实现，跳过超管状态同步。",
                localUserId, isSuperAdmin);
    }
}

