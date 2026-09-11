package com.sz.ssoclient.internal.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sz.ssoclient.api.browser.SsoLoginTransactionRequest;
import com.sz.ssoclient.api.browser.SsoLoginTransactionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.URI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SsoClientWebSecurityTest {

    private static final String ORIGIN = "https://client.example.com";
    private static final String BROWSER_ID = "B".repeat(43);

    private SsoClientWebService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(SsoClientWebService.class);
        when(service.createLoginTransaction(
                eq(BROWSER_ID), any(SsoLoginTransactionRequest.class)))
                .thenReturn(new SsoLoginTransactionResponse("https://sso.example.com/login"));
        SsoClientBrowserBinding binding = new SsoClientBrowserBinding(() -> BROWSER_ID, true);
        SsoClientWebController controller = new SsoClientWebController(
                service,
                binding,
                new SsoSameOriginGuard(URI.create(ORIGIN), false),
                new SsoStrictJsonGuard(new ObjectMapper()));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new SsoClientWebExceptionHandler())
                .build();
    }

    @Test
    void acceptsSameOriginAndAddsSensitiveResponseHeaders() throws Exception {
        mockMvc.perform(post("/sso/v1/login/transactions")
                        .header("Origin", ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"back\":\"/dashboard\",\"theme\":\"light\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.authorizationUrl")
                        .value("https://sso.example.com/login"))
                .andExpect(jsonPath("$.success").doesNotExist())
                .andExpect(jsonPath("$.requestId").doesNotExist());
    }

    @Test
    void rejectsUnknownJsonFieldsLocally() throws Exception {
        mockMvc.perform(post("/sso/v1/login/transactions")
                        .header("Origin", ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"back\":\"/dashboard\",\"mode\":\"simple\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(jsonPath("$.code").value("SSO-1001"));
    }

    @Test
    void rejectsCrossOriginAndMissingSource() throws Exception {
        mockMvc.perform(post("/sso/v1/login/transactions")
                        .header("Origin", "https://evil.example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"back\":\"/dashboard\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SSO-1002"));

        mockMvc.perform(post("/sso/v1/login/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"back\":\"/dashboard\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SSO-1002"));
    }

    @Test
    void acceptsSameOriginRefererWhenOriginIsAbsent() throws Exception {
        mockMvc.perform(post("/sso/v1/login/transactions")
                        .header("Referer", ORIGIN + "/dashboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"back\":\"/dashboard\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"));
    }
}
