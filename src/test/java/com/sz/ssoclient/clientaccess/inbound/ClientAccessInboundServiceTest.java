package com.sz.ssoclient.clientaccess.inbound;

import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.sso.template.SaSsoTemplate;
import cn.dev33.satoken.util.SaResult;
import com.sz.ssoclient.clientaccess.web.ClientLocalSessionAccessor;
import com.sz.ssoclient.message.SsoServerMessageDispatcher;
import com.sz.ssoclient.spi.SsoUserMappingService;
import com.sz.ssocore.SsoMessageTypes;
import com.sz.ssocore.clientaccess.v1.ClientAccessMessageTypes;
import com.sz.ssocore.clientaccess.v1.dto.ClientMessageEnvelope;
import com.sz.ssocore.clientaccess.v1.dto.ClientMessageStatus;
import com.sz.ssocore.clientaccess.v1.dto.SloCallbackRequest;
import com.sz.ssocore.clientaccess.v1.dto.SloCallbackStatus;
import com.sz.ssocore.clientaccess.v1.dto.SignoutScope;
import com.sz.ssocore.provisioning.SsoClientUserReadinessBatchResult;
import com.sz.ssocore.provisioning.SsoClientUserReadinessItem;
import com.sz.ssocore.provisioning.SsoClientUserReadinessStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientAccessInboundServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-13T03:00:00Z");

    private ClientLocalSessionAccessor sessions;
    private SsoUserMappingService mappings;
    private SsoServerMessageDispatcher dispatcher;
    private ClientAccessInboundService service;

    @BeforeEach
    void setUp() {
        sessions = mock(ClientLocalSessionAccessor.class);
        mappings = mock(SsoUserMappingService.class);
        dispatcher = mock(SsoServerMessageDispatcher.class);
        SaSsoTemplate template = mock(SaSsoTemplate.class);
        service = new ClientAccessInboundService(sessions, mappings, dispatcher, () -> template,
                new InMemoryClientInboundEventStore(Clock.fixed(NOW, ZoneOffset.UTC)),
                Clock.fixed(NOW, ZoneOffset.UTC), 3600);
    }

    @Test
    void sloCallbackIsIdempotentAndUsesCenterToLocalMapping() {
        when(mappings.toClientUserId("10001")).thenReturn(9001L);
        SloCallbackRequest request = new SloCallbackRequest("event-1", "10001",
                SignoutScope.CURRENT_DEVICE, "device-1", NOW, "SERVER_SIGNOUT");

        assertThat(service.handleSlo(request).status()).isEqualTo(SloCallbackStatus.APPLIED);
        assertThat(service.handleSlo(request).status()).isEqualTo(SloCallbackStatus.ALREADY_APPLIED);

        verify(sessions, times(1)).logoutCurrentDevice(9001L, "device-1");
    }

    @Test
    void messageEnvelopeReusesExistingDispatcherAndIsIdempotent() {
        when(dispatcher.dispatch(any(), any())).thenReturn(SaResult.ok());
        ClientMessageEnvelope envelope = new ClientMessageEnvelope("message-1",
                ClientAccessMessageTypes.CLIENT_SUPER_ADMIN_CHANGED,
                "1.0", NOW, NOW.plusSeconds(300), Map.of("centerId", "10001", "isSuperAdmin", true));

        assertThat(service.handleMessage(envelope).status()).isEqualTo(ClientMessageStatus.APPLIED);
        assertThat(service.handleMessage(envelope).status()).isEqualTo(ClientMessageStatus.ALREADY_APPLIED);

        ArgumentCaptor<SaSsoMessage> messageCaptor = ArgumentCaptor.forClass(SaSsoMessage.class);
        verify(dispatcher, times(1)).dispatch(any(), messageCaptor.capture());
        assertThat(messageCaptor.getValue().getType()).isEqualTo(SsoMessageTypes.SYNC_CLIENT_SUPER_ADMIN);
    }

    @Test
    void exposesBatchPartialFailureItemsThroughCapResult() {
        Map<String, Object> failItem = new LinkedHashMap<>();
        failItem.put("index", 2);
        failItem.put("centerId", "10002");
        failItem.put("reason", "apply failed");
        failItem.put("nickname", null);
        when(dispatcher.dispatch(any(), any())).thenReturn(SaResult.data(Map.of(
                "requested", 2,
                "success", 1,
                "failed", 1,
                "successIds", List.of("10001"),
                "failItems", List.of(failItem))));
        ClientMessageEnvelope envelope = new ClientMessageEnvelope("message-2",
                ClientAccessMessageTypes.CLIENT_SUPER_ADMIN_BATCH_CHANGED,
                "1.0", NOW, NOW.plusSeconds(300), Map.of("centerIds", "10001,10002"));

        var result = service.handleMessage(envelope);

        assertThat(result.status()).isEqualTo(ClientMessageStatus.PARTIALLY_APPLIED);
        assertThat(result.items()).containsExactly(failItem);
    }

    @Test
    void exposesReadinessBusinessItemsWithoutLeakingInternalMessageType() {
        SsoClientUserReadinessItem item = SsoClientUserReadinessItem.builder()
                .ssoUserId(10001L)
                .localUserId(9001L)
                .status(SsoClientUserReadinessStatus.READY)
                .preparable(true)
                .build();
        when(dispatcher.dispatch(any(), any())).thenReturn(SaResult.data(
                SsoClientUserReadinessBatchResult.builder()
                        .submittedCount(1)
                        .readyCount(1)
                        .preparableCount(1)
                        .items(List.of(item))
                        .build()));
        ClientMessageEnvelope envelope = new ClientMessageEnvelope("readiness-1",
                ClientAccessMessageTypes.CLIENT_USER_READINESS_CHECK,
                "1.0", NOW, NOW.plusSeconds(300), Map.of("centerIds", "10001"));

        var result = service.handleMessage(envelope);

        assertThat(result.status()).isEqualTo(ClientMessageStatus.APPLIED);
        assertThat(result.items()).singleElement().satisfies(resultItem -> assertThat(resultItem)
                .containsEntry("ssoUserId", 10001L)
                .containsEntry("localUserId", 9001L)
                .containsEntry("status", "READY")
                .containsEntry("preparable", true));
    }
}
