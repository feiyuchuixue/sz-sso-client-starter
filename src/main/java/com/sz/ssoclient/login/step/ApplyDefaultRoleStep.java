package com.sz.ssoclient.login.step;

import com.sz.ssoclient.login.LoginContext;
import com.sz.ssoclient.login.LoginStep;
import com.sz.ssoclient.spi.SsoClientRoleProvider;
import com.sz.ssoclient.spi.SsoRoleBindingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

/**
 * 首次登录默认角色初始化步骤.
 */
@Slf4j
@RequiredArgsConstructor
public class ApplyDefaultRoleStep<U> implements LoginStep<U> {

    @Nullable
    private final SsoClientRoleProvider roleProvider;

    @Nullable
    private final SsoRoleBindingService roleBindingService;

    @Override
    public void execute(LoginContext<U> context) {
        if (roleProvider == null || roleBindingService == null) {
            return;
        }
        try {
            roleBindingService.applyDefaultRole(context.getLocalUserId(), roleProvider.getDefaultRoleKey());
        } catch (Exception e) {
            log.warn("[SSO] 默认角色初始化异常，跳过: localUserId={}, error={}", context.getLocalUserId(), e.getMessage(), e);
        }
    }
}

