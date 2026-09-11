package com.sz.ssoclient.internal.web;

import com.sz.ssoclient.api.browser.SsoSignoutResponse;
import com.sz.ssoclient.internal.login.SsoClientLoginOrchestrator;
import com.sz.ssoclient.internal.login.transaction.SsoLoginTransactionService;
import com.sz.ssoclient.internal.messaging.SsoClientMessageGateway;
import com.sz.ssoclient.spi.SsoClientIdentityAdapter;
import com.sz.ssoclient.spi.SsoClientSessionHandle;
import com.sz.ssoclient.spi.advanced.SsoClientLocalSession;
import com.sz.ssoclient.spi.advanced.SsoClientLocalSessionAccessor;
import com.sz.ssocore.SsoMessageResult;
import com.sz.ssocore.SsoMessageTypes;
import com.sz.ssocore.signout.SsoLocalOutcome;
import com.sz.ssocore.signout.SsoPropagationOutcome;
import com.sz.ssocore.signout.SsoPropagationReason;
import com.sz.ssocore.signout.SsoSignoutReceipt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SsoClientSignoutServiceTest {

    private final SsoLoginTransactionService transactions = mock(SsoLoginTransactionService.class);
    private final SsoClientLoginOrchestrator<?> login = mock(SsoClientLoginOrchestrator.class);
    private final SsoClientMessageGateway gateway = mock(SsoClientMessageGateway.class);
    private final SsoClientIdentityAdapter identities = mock(SsoClientIdentityAdapter.class);
    private final SsoClientLocalSessionAccessor sessions = mock(SsoClientLocalSessionAccessor.class);
    private final SsoClientWebService.LoginAuthorizationUrlFactory loginUrls = mock(
            SsoClientWebService.LoginAuthorizationUrlFactory.class);
    private final SsoClientWebService.PortalUrlFactory portalUrls = mock(
            SsoClientWebService.PortalUrlFactory.class);

    private SsoClientWebService service;
    private SsoClientLocalSession current;

    @BeforeEach
    void setUp() {
        service = new SsoClientWebService(
                transactions, login, gateway, identities, sessions, loginUrls, portalUrls);
        current = new SsoClientLocalSession(
                true, "local-7", "device-9", new SsoClientSessionHandle("session-11"));
        when(sessions.current()).thenReturn(current);
        when(identities.findSsoUserId("local-7")).thenReturn(Optional.of(101L));
        when(sessions.revokeDevice("local-7", "device-9"))
                .thenReturn(SsoLocalOutcome.REVOKED);
        when(sessions.revokeAccount("local-7")).thenReturn(SsoLocalOutcome.REVOKED);
    }

    @Test
    void deviceSignoutPreservesBothAcceptedAxesAndReceipt() {
        SsoSignoutReceipt receipt = new SsoSignoutReceipt(
                "signout-1", Instant.parse("2026-08-04T00:00:00Z"), 3);
        when(gateway.send(eq(SsoMessageTypes.REQUEST_SLO), any(), eq(SsoSignoutReceipt.class)))
                .thenReturn(new SsoMessageResult<>("0000", "success", receipt));

        SsoSignoutResponse result = service.signoutCurrentDevice();

        assertThat(result.localOutcome()).isEqualTo(SsoLocalOutcome.REVOKED);
        assertThat(result.propagationOutcome()).isEqualTo(SsoPropagationOutcome.ACCEPTED);
        assertThat(result.propagationReason()).isNull();
        assertThat(result.receipt()).isEqualTo(receipt);
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Map<String, Object>> payload =
                (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
        verify(gateway).send(eq(SsoMessageTypes.REQUEST_SLO), payload.capture(),
                eq(SsoSignoutReceipt.class));
        assertThat(payload.getValue()).containsEntry("ssoUserId", 101L)
                .containsEntry("scope", "CURRENT_DEVICE")
                .containsEntry("deviceId", "device-9");
    }

    @Test
    void accountSignoutKeepsLocalResultWhenServerRejects() {
        when(gateway.send(eq(SsoMessageTypes.REQUEST_SLO), any(), eq(SsoSignoutReceipt.class)))
                .thenReturn(new SsoMessageResult<>("403", "rejected", null));

        SsoSignoutResponse result = service.signoutAccount();

        assertThat(result.localOutcome()).isEqualTo(SsoLocalOutcome.REVOKED);
        assertThat(result.propagationOutcome()).isEqualTo(SsoPropagationOutcome.REJECTED);
        assertThat(result.propagationReason()).isNull();
        assertThat(result.receipt()).isNull();
    }

    @Test
    void missingMappingDoesNotTriggerJitAndReturnsStableReason() {
        when(identities.findSsoUserId("local-7")).thenReturn(Optional.empty());

        SsoSignoutResponse result = service.signoutCurrentDevice();

        assertThat(result.localOutcome()).isEqualTo(SsoLocalOutcome.REVOKED);
        assertThat(result.propagationOutcome()).isEqualTo(SsoPropagationOutcome.NOT_REQUESTED);
        assertThat(result.propagationReason()).isEqualTo(SsoPropagationReason.NO_MAPPING);
        assertThat(result.receipt()).isNull();
    }

    @Test
    void uncertainPropagationNeverMasksFailedLocalAxis() {
        when(sessions.revokeDevice("local-7", "device-9"))
                .thenReturn(SsoLocalOutcome.FAILED);
        when(gateway.send(eq(SsoMessageTypes.REQUEST_SLO), any(), eq(SsoSignoutReceipt.class)))
                .thenThrow(new IllegalStateException("network outcome unknown"));

        SsoSignoutResponse result = service.signoutCurrentDevice();

        assertThat(result.localOutcome()).isEqualTo(SsoLocalOutcome.FAILED);
        assertThat(result.propagationOutcome()).isEqualTo(SsoPropagationOutcome.UNCONFIRMED);
        assertThat(result.propagationReason()).isNull();
        assertThat(result.receipt()).isNull();
    }
}
