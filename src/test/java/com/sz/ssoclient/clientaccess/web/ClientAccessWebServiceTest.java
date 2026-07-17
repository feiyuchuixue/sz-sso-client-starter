package com.sz.ssoclient.clientaccess.web;

import com.sz.ssoclient.clientaccess.http.ClientAccessV1Client;
import com.sz.ssoclient.spi.SsoUserMappingService;
import com.sz.ssocore.clientaccess.v1.dto.ClientAccessResponse;
import com.sz.ssocore.clientaccess.v1.dto.PortalTicketCreateResponse;
import com.sz.ssocore.clientaccess.v1.dto.SignoutCreateResponse;
import com.sz.ssocore.clientaccess.v1.dto.SignoutScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientAccessWebServiceTest {

    private ClientAccessV1Client capClient;
    private ClientLocalSessionAccessor sessions;
    private SsoUserMappingService mappings;
    private ClientAccessWebService service;

    @BeforeEach
    void setUp() {
        capClient = mock(ClientAccessV1Client.class);
        sessions = mock(ClientLocalSessionAccessor.class);
        mappings = mock(SsoUserMappingService.class);
        service = new ClientAccessWebService(capClient, sessions, mappings, () -> "idempotency-key");
        when(sessions.current()).thenReturn(new ClientLocalSession(true, 9001L, "device-1", Instant.parse("2026-07-13T05:00:00Z")));
        when(mappings.toServerUserId(9001L)).thenReturn("10001");
    }

    @Test
    void portalIdentityComesOnlyFromLocalSession() {
        PortalTicketCreateResponse portal = new PortalTicketCreateResponse(
                "https://auth.example.com/portal-login?ticket=redacted", "/ucenter/profile",
                Instant.parse("2026-07-13T03:01:00Z"));
        when(capClient.createPortalTicket(any(), anyString())).thenReturn(success(portal));

        PortalTicketCreateResponse result = service.createPortalEntry("/ucenter/profile");

        assertThat(result.portalUrl()).isEqualTo(portal.portalUrl());
        verify(capClient).createPortalTicket(
                new com.sz.ssocore.clientaccess.v1.dto.PortalTicketCreateRequest("10001", "/ucenter/profile"),
                "idempotency-key");
    }

    @Test
    void deviceSignoutCallsServerBeforeRevokingLocalDevice() {
        SignoutCreateResponse accepted = new SignoutCreateResponse(
                "signout-1", SignoutScope.CURRENT_DEVICE, Instant.parse("2026-07-13T03:00:00Z"));
        when(capClient.createSignout(any(), anyString())).thenReturn(success(accepted));

        SignoutCreateResponse result = service.signoutCurrentDevice();

        assertThat(result.scope()).isEqualTo(SignoutScope.CURRENT_DEVICE);
        verify(sessions).logoutCurrentDevice(9001L, "device-1");
    }

    @Test
    void accountSignoutRevokesAllLocalSessionsForMappedUser() {
        SignoutCreateResponse accepted = new SignoutCreateResponse(
                "signout-2", SignoutScope.ACCOUNT_GLOBAL, Instant.parse("2026-07-13T03:00:00Z"));
        when(capClient.createSignout(any(), anyString())).thenReturn(success(accepted));

        service.signoutAccount();

        verify(sessions).logoutAccount(9001L);
    }

    private static <T> ClientAccessResponse<T> success(T data) {
        return new ClientAccessResponse<>(true, "CAP-0000", "success", "request", Instant.now(), data, List.of());
    }
}
