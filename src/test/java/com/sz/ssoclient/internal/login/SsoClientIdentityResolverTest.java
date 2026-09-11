package com.sz.ssoclient.internal.login;

import com.sz.ssoclient.internal.messaging.SsoClientMessageGateway;
import com.sz.ssoclient.spi.SsoClientIdentityAdapter;
import com.sz.ssocore.SsoMessageResult;
import com.sz.ssocore.SsoMessageTypes;
import com.sz.ssocore.SsoUserMeta;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SsoClientIdentityResolverTest {

    @Test
    void existingMappingIsReadOnlyAndDoesNotQueryOrProvision() {
        SsoClientIdentityAdapter identity = mock(SsoClientIdentityAdapter.class);
        SsoClientMessageGateway gateway = mock(SsoClientMessageGateway.class);
        when(identity.findLocalUserId(42L)).thenReturn(Optional.of("tenant:user-42"));

        String localUserId = new SsoClientIdentityResolver(identity, gateway).resolve(42L);

        assertEquals("tenant:user-42", localUserId);
        verify(gateway, never()).send(any(), any(), any());
        verify(identity, never()).resolveOrProvision(any());
    }

    @Test
    void missingMappingQueriesNeutralUserMetaThenCallsHostJitOnce() {
        SsoClientIdentityAdapter identity = mock(SsoClientIdentityAdapter.class);
        SsoClientMessageGateway gateway = mock(SsoClientMessageGateway.class);
        SsoUserMeta meta = SsoUserMeta.builder().ssoUserId(42L).username("alice").build();
        when(identity.findLocalUserId(42L)).thenReturn(Optional.empty());
        when(gateway.send(
                eq(SsoMessageTypes.USER_CHECK),
                eq(Map.of("ssoUserId", 42L)),
                eq(SsoUserMeta.class)))
                .thenReturn(new SsoMessageResult<>(SsoMessageResult.SUCCESS_CODE, "ok", meta));
        when(identity.resolveOrProvision(meta)).thenReturn("tenant:user-42");

        String localUserId = new SsoClientIdentityResolver(identity, gateway).resolve(42L);

        assertEquals("tenant:user-42", localUserId);
        verify(identity).resolveOrProvision(meta);
    }

    @Test
    void failedMissingOrMismatchedUserMetaNeverReachesHostJit() {
        SsoClientIdentityAdapter identity = mock(SsoClientIdentityAdapter.class);
        SsoClientMessageGateway gateway = mock(SsoClientMessageGateway.class);
        when(identity.findLocalUserId(42L)).thenReturn(Optional.empty());
        when(gateway.send(any(), any(), eq(SsoUserMeta.class)))
                .thenReturn(new SsoMessageResult<>("USER_NOT_FOUND", "not found", null));
        SsoClientIdentityResolver resolver = new SsoClientIdentityResolver(identity, gateway);

        assertThrows(IllegalStateException.class, () -> resolver.resolve(42L));
        verify(identity, never()).resolveOrProvision(any());

        when(gateway.send(any(), any(), eq(SsoUserMeta.class)))
                .thenReturn(new SsoMessageResult<>(
                        SsoMessageResult.SUCCESS_CODE,
                        "ok",
                        SsoUserMeta.builder().ssoUserId(43L).build()));
        assertThrows(IllegalStateException.class, () -> resolver.resolve(42L));
        verify(identity, never()).resolveOrProvision(any());
    }
}
