package com.sz.ssoclient.clientaccess.web;

import com.sz.ssoclient.clientaccess.http.ClientAccessRegistration;
import com.sz.ssoclient.clientaccess.http.ClientAccessRegistrationProvider;
import com.sz.ssoclient.clientaccess.http.ClientAccessV1Client;
import com.sz.ssoclient.pojo.SsoLoginResult;
import com.sz.ssoclient.service.SsoClientService;
import com.sz.ssoclient.spi.SsoUserMappingService;
import com.sz.ssocore.clientaccess.v1.ClientAccessSigner;
import com.sz.ssocore.clientaccess.v1.dto.AuthenticationContext;
import com.sz.ssocore.clientaccess.v1.dto.ClientAccessResponse;
import com.sz.ssocore.clientaccess.v1.dto.ClientAccessUser;
import com.sz.ssocore.clientaccess.v1.dto.ClientAuthorities;
import com.sz.ssocore.clientaccess.v1.dto.LoginAuthorizationResponse;
import com.sz.ssocore.clientaccess.v1.dto.LoginTicketExchangeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientLoginTransactionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-13T03:00:00Z");

    private ClientAccessV1Client capClient;
    private SsoUserMappingService mappingService;
    private SsoClientService loginService;
    private InMemoryClientLoginTransactionStore store;
    private ClientLoginTransactionService service;

    @BeforeEach
    void setUp() {
        capClient = mock(ClientAccessV1Client.class);
        mappingService = mock(SsoUserMappingService.class);
        loginService = mock(SsoClientService.class);
        store = new InMemoryClientLoginTransactionStore();
        ClientAccessRegistration registration = new ClientAccessRegistration(
                URI.create("https://sso.example.com"), "client-demo",
                "secret-value".getBytes(StandardCharsets.UTF_8));
        ClientAccessRegistrationProvider registrationProvider = () -> registration;
        AtomicInteger sequence = new AtomicInteger();
        service = new ClientLoginTransactionService(capClient, registrationProvider,
                mappingService, loginService, store, Clock.fixed(NOW, ZoneOffset.UTC),
                () -> "0123456789abcdefghijklmnopqrstuv" + sequence.incrementAndGet(),
                600, 5, 7200);
    }

    @Test
    void createsHashedBrowserBoundTransactionAndCompletesOnlyOnce() {
        AtomicReference<String> callbackState = new AtomicReference<>();
        when(capClient.authorize(any(), anyString())).thenAnswer(invocation -> {
            com.sz.ssocore.clientaccess.v1.dto.LoginAuthorizationRequest request = invocation.getArgument(0);
            callbackState.set(request.state());
            return success(new LoginAuthorizationResponse("auth-001",
                    "https://auth.example.com/login?r=auth-001", NOW.plusSeconds(600)));
        });
        when(capClient.exchangeTicket(any(), anyString())).thenReturn(success(
                new LoginTicketExchangeResponse(
                        new ClientAccessUser("10001", "alice", "Alice", null, null, null),
                        new ClientAuthorities(true),
                        new AuthenticationContext(NOW, "device-001", "password"))));
        when(mappingService.resolveOrProvisionClientUser("10001")).thenReturn(9001L);
        SsoLoginResult localResult = SsoLoginResult.of("local-token", 7200L, "local-user");
        when(loginService.login(any(com.sz.ssoclient.login.SsoLoginCommand.class))).thenReturn(localResult);

        ClientLoginTransactionCreated created = service.create("browser-session-a", "/workspace", "default", "light");
        ClientLoginCallbackResult first = service.complete("browser-session-a", callbackState.get(), "LT-one-time");
        ClientLoginCallbackResult repeated = service.complete("browser-session-a", callbackState.get(), "LT-one-time");

        assertThat(created.authorizationUrl()).contains("auth-001");
        ClientLoginTransaction completed = store.find(ClientAccessSigner.bodySha256(
                callbackState.get().getBytes(StandardCharsets.UTF_8)));
        assertThat(completed.status()).isEqualTo(ClientLoginTransactionStatus.COMPLETED);
        assertThat(first.back()).isEqualTo("/workspace");
        assertThat(repeated.loginResult().getAccessToken()).isEqualTo("local-token");
        verify(capClient).exchangeTicket(any(), anyString());
    }

    @Test
    void rejectsCrossBrowserCallbackBeforeTicketExchange() {
        AtomicReference<String> callbackState = new AtomicReference<>();
        when(capClient.authorize(any(), anyString())).thenAnswer(invocation -> {
            com.sz.ssocore.clientaccess.v1.dto.LoginAuthorizationRequest request = invocation.getArgument(0);
            callbackState.set(request.state());
            return success(new LoginAuthorizationResponse("auth-002",
                    "https://auth.example.com/login?r=auth-002", NOW.plusSeconds(600)));
        });
        service.create("browser-session-a", "/", null, null);

        assertThatThrownBy(() -> service.complete("browser-session-b", callbackState.get(), "LT-stolen"))
                .isInstanceOf(ClientLoginTransactionException.class)
                .extracting("code")
                .isEqualTo("CAP-3002");
        verify(capClient, never()).exchangeTicket(any(), anyString());
    }

    @Test
    void rejectsUnsafeBackBeforeCallingServer() {
        assertThatThrownBy(() -> service.create("browser-session-a", "//evil.example.com", null, null))
                .isInstanceOf(ClientLoginTransactionException.class)
                .extracting("code")
                .isEqualTo("CAP-1001");
        verify(capClient, never()).authorize(any(), anyString());
    }

    @Test
    void browserTransactionResponseExposesOnlyAuthorizationUrlAndExpiry() {
        assertThat(ClientLoginTransactionCreated.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("authorizationUrl", "expiresAt");
    }

    @Test
    void reservesBrowserTransactionBeforeCallingServerAuthorization() {
        AtomicReference<String> reservedState = new AtomicReference<>();
        when(capClient.authorize(any(), anyString())).thenAnswer(invocation -> {
            com.sz.ssocore.clientaccess.v1.dto.LoginAuthorizationRequest request = invocation.getArgument(0);
            reservedState.set(request.state());
            ClientLoginTransaction reserved = store.find(ClientAccessSigner.bodySha256(
                    request.state().getBytes(StandardCharsets.UTF_8)));
            assertThat(reserved).isNotNull();
            assertThat(reserved.authorizationRequestId()).isNull();
            return success(new LoginAuthorizationResponse("auth-reserved",
                    "https://auth.example.com/login?r=auth-reserved", NOW.plusSeconds(600)));
        });

        service.create("browser-session-a", "/workspace", null, null);

        ClientLoginTransaction authorized = store.find(ClientAccessSigner.bodySha256(
                reservedState.get().getBytes(StandardCharsets.UTF_8)));
        assertThat(authorized.authorizationRequestId()).isEqualTo("auth-reserved");
        assertThat(authorized.status()).isEqualTo(ClientLoginTransactionStatus.CREATED);
    }

    private static <T> ClientAccessResponse<T> success(T data) {
        return new ClientAccessResponse<>(true, "CAP-0000", "success", "request-001", NOW, data, List.of());
    }
}
