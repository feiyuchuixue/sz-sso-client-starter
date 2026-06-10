package com.sz.ssoclient.login;

import cn.dev33.satoken.sso.model.SaCheckTicketResult;
import com.sz.ssoclient.pojo.SsoLoginResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SsoLoginOrchestrator 单元测试")
class SsoLoginOrchestratorTest {

    @Test
    @DisplayName("login() 应按顺序执行责任链步骤并返回上下文结果")
    void login_shouldRunStepsInOrder() {
        List<String> calls = new ArrayList<>();
        SsoLoginResult expected = SsoLoginResult.of("token", 60L, "user");
        SsoLoginOrchestrator<String> orchestrator = new SsoLoginOrchestrator<>(List.of(
                context -> calls.add("prepare"),
                context -> calls.add("build"),
                context -> {
                    calls.add("result");
                    context.setResult(expected);
                }
        ));

        SsoLoginResult actual = orchestrator.login(new SaCheckTicketResult());

        assertThat(calls).containsExactly("prepare", "build", "result");
        assertThat(actual).isSameAs(expected);
    }
}

