package com.sz.sso.client;

import lombok.extern.slf4j.Slf4j;

/**
 * SsoRoleBindingService 的内置默认实现.
 * <p>
 * 当业务方未自定义 {@link SsoRoleBindingService} Bean 时自动生效。
 * 默认行为：打印 warn 日志提醒业务方实现该接口，不做任何实际操作。
 * </p>
 *
 * @author sz
 * @version 3.0
 * @since 2025/6/23
 * @see SsoRoleBindingService
 */
@Slf4j
public class DefaultSsoRoleBindingService implements SsoRoleBindingService {

    @Override
    public void applyDefaultRole(Long localUserId, String defaultRoleKey) {
        log.warn("[SSO] 用户 {} 首次登录，平台建议默认角色 key={}，" +
                 "但未找到 SsoRoleBindingService 实现，跳过默认角色初始化。",
                 localUserId, defaultRoleKey);
    }

    @Override
    public void applySuperAdmin(Long localUserId, boolean isSuperAdmin) {
        log.warn("[SSO] 用户 {} 超管状态={}，" +
                 "但未找到 SsoRoleBindingService 实现，跳过超管状态同步。",
                 localUserId, isSuperAdmin);
    }

}
