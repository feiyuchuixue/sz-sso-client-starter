package com.sz.ssoclient.internal.messaging;

import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import cn.dev33.satoken.util.SaResult;
import com.sz.ssoclient.spi.SsoClientIdentityAdapter;
import com.sz.ssocore.SsoMessageResult;
import com.sz.ssocore.SsoMessageTypes;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SsoReservedMessageBoundaryTest {

    @Test
    void customSenderRejectsReservedTypeAndEnvelopeFieldsBeforeTransport() {
        SaSsoClientTemplate template = mock(SaSsoClientTemplate.class);
        when(template.getClient()).thenReturn("client-a");
        SaTokenSsoMessageTransport transport = new SaTokenSsoMessageTransport(template, new SsoMessageCodec());

        assertThrows(IllegalArgumentException.class,
                () -> transport.sendCustom(SsoMessageTypes.USER_CHECK, Map.of(), Void.class));
        for (String field : new String[] {
                "client", "clientId", "type", "msgType", "sign", "timestamp", "nonce"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> transport.sendCustom("CUSTOM_AUDIT", Map.of(field, "forged"), Void.class), field);
        }

        verify(template, never()).pushMessageAsSaResult(any());
    }

    @Test
    void customSenderIsSynchronousAndReturnsRealTypedResult() {
        SaSsoClientTemplate template = mock(SaSsoClientTemplate.class);
        when(template.getClient()).thenReturn("client-a");
        when(template.pushMessageAsSaResult(any())).thenReturn(SaResult.data(Map.of("value", "ok")));
        SaTokenSsoMessageTransport transport = new SaTokenSsoMessageTransport(template, new SsoMessageCodec());

        SsoMessageResult<Map> result = transport.sendCustom("CUSTOM_AUDIT", Map.of("event", "login"), Map.class);

        assertEquals(SsoMessageResult.SUCCESS_CODE, result.code());
        assertEquals("ok", result.data().get("value"));
        verify(template).pushMessageAsSaResult(any());
    }

    @Test
    void superAdminOperationMapsOpaqueLocalIdAndPropagatesTransportResult() {
        SaTokenSsoMessageTransport transport = mock(SaTokenSsoMessageTransport.class);
        SsoClientIdentityAdapter identityAdapter = mock(SsoClientIdentityAdapter.class);
        when(identityAdapter.findSsoUserId("tenant:user-a")).thenReturn(Optional.of(42L));
        SsoMessageResult<Void> expected = new SsoMessageResult<>("SERVER_REJECTED", "rejected", null);
        when(transport.sendTrusted(eq(SsoMessageTypes.SYNC_SUPER_ADMIN), any(), eq(Void.class)))
                .thenReturn(expected);
        SsoClientMessageGateway gateway = new SsoClientMessageGateway(transport, identityAdapter);

        SsoMessageResult<Void> actual = gateway.syncSuperAdmin("tenant:user-a", true);

        assertSame(expected, actual);
        verify(transport).sendTrusted(
                eq(SsoMessageTypes.SYNC_SUPER_ADMIN),
                eq(Map.of("ssoUserId", 42L, "isSuperAdmin", true)),
                eq(Void.class));
    }
}
