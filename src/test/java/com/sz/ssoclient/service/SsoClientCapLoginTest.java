package com.sz.ssoclient.service;

import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.sz.ssoclient.login.SsoLoginCommand;
import com.sz.ssoclient.login.SsoLoginOrchestrator;
import com.sz.ssoclient.login.step.ApplySuperAdminStep;
import com.sz.ssoclient.login.step.BuildLoginUserStep;
import com.sz.ssoclient.login.step.CreateLoginResultStep;
import com.sz.ssoclient.login.step.PrepareLoginParameterStep;
import com.sz.ssoclient.pojo.SsoLoginResult;
import com.sz.ssoclient.service.impl.SsoClientServiceImpl;
import com.sz.ssoclient.spi.SsoClientLoginAdapter;
import com.sz.ssoclient.spi.SsoRoleBindingService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SsoClientCapLoginTest {

    @Test
    void capLoginUsesLocalMappingSessionPolicyAndServerScopedAuthority() {
        @SuppressWarnings("unchecked")
        SsoClientLoginAdapter<String> loginAdapter = mock(SsoClientLoginAdapter.class);
        SsoRoleBindingService roleBindingService = mock(SsoRoleBindingService.class);
        when(loginAdapter.buildLoginUser(9001L)).thenReturn("local-user");
        when(loginAdapter.createLoginResult(eq("local-user"), any(SaLoginParameter.class), eq(9001L)))
                .thenReturn(SsoLoginResult.of("local-token", 7200L, "local-user"));
        SsoLoginOrchestrator<String> orchestrator = new SsoLoginOrchestrator<>(List.of(
                new PrepareLoginParameterStep<>(),
                new ApplySuperAdminStep<>(roleBindingService),
                new BuildLoginUserStep<>(loginAdapter),
                new CreateLoginResultStep<>(loginAdapter)));
        SsoClientService service = new SsoClientServiceImpl<>(orchestrator);

        SsoLoginResult result = service.login(new SsoLoginCommand(
                "10001", 9001L, "device-001", 7200L, true));

        assertThat(result.getAccessToken()).isEqualTo("local-token");
        verify(roleBindingService).applySuperAdmin(9001L, true);
        verify(loginAdapter).createLoginResult(eq("local-user"),
                org.mockito.ArgumentMatchers.argThat(parameter ->
                        "device-001".equals(parameter.getDeviceId()) && parameter.getTimeout() == 7200L),
                eq(9001L));
    }
}
