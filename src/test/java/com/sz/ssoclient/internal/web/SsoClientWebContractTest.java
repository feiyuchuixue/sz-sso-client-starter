package com.sz.ssoclient.internal.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sz.ssoclient.api.browser.SsoLoginCallbackRequest;
import com.sz.ssoclient.api.browser.SsoLoginCallbackResponse;
import com.sz.ssoclient.api.browser.SsoLoginTransactionRequest;
import com.sz.ssoclient.api.browser.SsoLoginTransactionResponse;
import com.sz.ssoclient.api.browser.SsoPortalEntryRequest;
import com.sz.ssoclient.api.browser.SsoPortalEntryResponse;
import com.sz.ssoclient.api.browser.SsoPortalTarget;
import com.sz.ssoclient.api.browser.SsoWebResult;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SsoClientWebContractTest {

    @Test
    void exposesExactlySixPostRoutes() {
        String root = SsoClientWebController.class
                .getAnnotation(RequestMapping.class).value()[0];
        Set<String> routes = Arrays.stream(SsoClientWebController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostMapping.class))
                .map(method -> "POST " + root
                        + method.getAnnotation(PostMapping.class).value()[0])
                .collect(Collectors.toSet());

        assertThat(routes).containsExactlyInAnyOrder(
                "POST /sso/v1/login/transactions",
                "POST /sso/v1/login/callback",
                "POST /sso/v1/session/logout",
                "POST /sso/v1/signouts/device",
                "POST /sso/v1/signouts/account",
                "POST /sso/v1/portal/entries");
        assertThat(Arrays.stream(SsoClientWebController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class)))
                .isEmpty();
    }

    @Test
    void exposesOnlyFrozenBrowserDtoFields() {
        assertRecordComponents(SsoLoginTransactionRequest.class, "back", "theme");
        assertRecordComponents(SsoLoginTransactionResponse.class, "authorizationUrl");
        assertRecordComponents(SsoLoginCallbackRequest.class, "ticket", "state");
        assertRecordComponents(SsoLoginCallbackResponse.class, "accessToken", "back");
        assertRecordComponents(SsoPortalEntryRequest.class, "target");
        assertRecordComponents(SsoPortalEntryResponse.class, "portalUrl", "targetPath");
        assertThat(SsoPortalTarget.values()).containsExactly(
                SsoPortalTarget.APPLICATIONS,
                SsoPortalTarget.PROFILE,
                SsoPortalTarget.ACCOUNT_SECURITY,
                SsoPortalTarget.LOGIN_LOG);
    }

    @Test
    void serializesOnlyCodeMessageAndData() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.readTree(mapper.writeValueAsBytes(
                SsoWebResult.success(new SsoLoginTransactionResponse("https://sso.example/login"))));

        assertThat(json.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("code", "message", "data");
        assertThat(json.path("code").asText()).isEqualTo("0000");
        assertThat(json.path("message").asText()).isEqualTo("success");
    }

    private static void assertRecordComponents(Class<?> type, String... names) {
        assertThat(Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName))
                .containsExactly(names);
    }
}
