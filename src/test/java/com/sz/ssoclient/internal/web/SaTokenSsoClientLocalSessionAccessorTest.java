package com.sz.ssoclient.internal.web;

import cn.dev33.satoken.session.SaTerminalInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SaTokenSsoClientLocalSessionAccessorTest {

    @Test
    void loginIdCandidatesPreserveOpaqueIdsAndAddCanonicalLong() {
        assertThat(SaTokenSsoClientLocalSessionAccessor.loginIdCandidates("local-user"))
                .containsExactly("local-user");
        assertThat(SaTokenSsoClientLocalSessionAccessor.loginIdCandidates("42"))
                .containsExactly("42", 42L);
        assertThat(SaTokenSsoClientLocalSessionAccessor.loginIdCandidates("0042"))
                .containsExactly("0042");
    }

    @Test
    void deviceMatchingUsesTerminalDeviceIdInsteadOfDeviceType() {
        List<SaTerminalInfo> terminals = List.of(
                new SaTerminalInfo().setDeviceType("browser").setDeviceId("device-a"),
                new SaTerminalInfo().setDeviceType("browser").setDeviceId("device-b"));

        assertThat(SaTokenSsoClientLocalSessionAccessor.hasDeviceTerminal(
                terminals, "device-a")).isTrue();
        assertThat(SaTokenSsoClientLocalSessionAccessor.hasDeviceTerminal(
                terminals, "browser")).isFalse();
    }
}
