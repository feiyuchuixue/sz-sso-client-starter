package com.sz.ssoclient.internal.messaging.handler;

import com.sz.ssoclient.spi.SsoClientIdentityAdapter;
import com.sz.ssoclient.spi.SsoSuperAdminSnapshotProvider;
import com.sz.ssocore.SsoMessageResult;
import com.sz.ssocore.superadmin.SsoSuperAdminSnapshotResult;
import com.sz.ssocore.superadmin.SsoSuperAdminSnapshotStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SsoClientSuperAdminSnapshotHandlerTest {

    @Test
    void missingProviderIsUnsupported() {
        SsoClientSuperAdminSnapshotHandler handler = new SsoClientSuperAdminSnapshotHandler(
                mock(SsoClientIdentityAdapter.class), null);

        SsoSuperAdminSnapshotResult result = handler.handle(Map.of()).data();

        assertEquals(SsoSuperAdminSnapshotStatus.UNSUPPORTED, result.status());
        assertTrue(result.ssoUserIds().isEmpty());
    }

    @Test
    void nullBlankDuplicateIncompleteAndExceptionResultsAreFailed() {
        SsoClientIdentityAdapter identity = mock(SsoClientIdentityAdapter.class);

        assertFailed(handler(identity, () -> null));
        assertFailed(handler(identity, () -> List.of(" ")));
        assertFailed(handler(identity, () -> List.of("local-a", "local-a")));

        when(identity.findSsoUserIds(any())).thenReturn(Map.of("local-a", 1L));
        assertFailed(handler(identity, () -> List.of("local-a", "local-b")));

        assertFailed(handler(identity, () -> {
            throw new IllegalStateException("database unavailable");
        }));
    }

    @Test
    void onlyCompleteUniqueMappingIsAuthoritative() {
        SsoClientIdentityAdapter identity = mock(SsoClientIdentityAdapter.class);
        when(identity.findSsoUserIds(any())).thenReturn(Map.of("tenant:a", 11L, "tenant:b", 12L));
        SsoClientSuperAdminSnapshotHandler handler = handler(
                identity,
                () -> List.of("tenant:a", "tenant:b"));

        SsoMessageResult<SsoSuperAdminSnapshotResult> messageResult = handler.handle(Map.of());

        assertEquals(SsoMessageResult.SUCCESS_CODE, messageResult.code());
        assertEquals(SsoSuperAdminSnapshotStatus.AUTHORITATIVE, messageResult.data().status());
        assertEquals(java.util.Set.of(11L, 12L), messageResult.data().ssoUserIds());
    }

    @Test
    void emptyCompleteSnapshotIsAuthoritative() {
        SsoClientIdentityAdapter identity = mock(SsoClientIdentityAdapter.class);
        when(identity.findSsoUserIds(any())).thenReturn(Map.of());

        SsoSuperAdminSnapshotResult result = handler(identity, List::of).handle(Map.of()).data();

        assertEquals(SsoSuperAdminSnapshotStatus.AUTHORITATIVE, result.status());
        assertTrue(result.ssoUserIds().isEmpty());
    }

    private static SsoClientSuperAdminSnapshotHandler handler(
            SsoClientIdentityAdapter identity,
            SsoSuperAdminSnapshotProvider provider) {
        return new SsoClientSuperAdminSnapshotHandler(identity, provider);
    }

    private static void assertFailed(SsoClientSuperAdminSnapshotHandler handler) {
        SsoSuperAdminSnapshotResult result = handler.handle(Map.of()).data();
        assertEquals(SsoSuperAdminSnapshotStatus.FAILED, result.status());
        assertTrue(result.ssoUserIds().isEmpty());
        assertTrue(result.failureReason() != null && !result.failureReason().isBlank());
    }
}
