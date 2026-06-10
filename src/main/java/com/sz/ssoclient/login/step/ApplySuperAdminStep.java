package com.sz.ssoclient.login.step;

import com.sz.ssoclient.login.LoginContext;
import com.sz.ssoclient.login.LoginStep;
import com.sz.ssoclient.spi.SsoRoleBindingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

/**
 * 同步平台超管状态到 Client 本地权限体系.
 */
@Slf4j
@RequiredArgsConstructor
public class ApplySuperAdminStep<U> implements LoginStep<U> {

    @Nullable
    private final SsoRoleBindingService roleBindingService;

    @Override
    public void execute(LoginContext<U> context) {
        if (roleBindingService == null) {
            return;
        }
        try {
            roleBindingService.applySuperAdmin(context.getLocalUserId(), context.isSuperAdmin());
        } catch (Exception e) {
            log.warn("[SSO] 超管状态同步异常，跳过: localUserId={}, isSuperAdmin={}, error={}",
                    context.getLocalUserId(), context.isSuperAdmin(), e.getMessage(), e);
        }
    }
}

