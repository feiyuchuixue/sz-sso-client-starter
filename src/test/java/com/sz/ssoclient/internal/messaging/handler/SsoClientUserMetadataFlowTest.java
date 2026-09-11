package com.sz.ssoclient.internal.messaging.handler;

import com.sz.ssoclient.internal.messaging.SsoClientMessageGateway;
import com.sz.ssoclient.spi.SsoClientIdentityAdapter;
import com.sz.ssoclient.spi.SsoClientIdentityPreparationService;
import com.sz.ssocore.SsoMessageResult;
import com.sz.ssocore.SsoProtocolFields;
import com.sz.ssocore.SsoUserMeta;
import com.sz.ssocore.provisioning.SsoClientGrantPurpose;
import com.sz.ssocore.provisioning.SsoClientUserPreparationBatchResult;
import com.sz.ssocore.provisioning.SsoClientUserReadinessBatchResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SsoClientUserMetadataFlowTest {

    private final SsoClientMessageGateway gateway = mock(SsoClientMessageGateway.class);
    private final SsoClientIdentityPreparationService preparationService =
            mock(SsoClientIdentityPreparationService.class);

    @Test
    void readinessMustResolveTrustedUserMetadataInsideStarterBeforeCallingHostSpi() {
        List<Long> ids = List.of(1001L, 1002L);
        List<SsoUserMeta> users = List.of(meta(1001L), meta(1002L));
        SsoClientUserReadinessBatchResult expected =
                SsoClientUserReadinessBatchResult.builder().submittedCount(2).build();
        when(gateway.fetchUserMetas(ids)).thenReturn(users);
        when(preparationService.checkUsers(users, SsoClientGrantPurpose.ADMIN_GRANT))
                .thenReturn(expected);
        SsoClientUserReadinessBatchHandler handler = new SsoClientUserReadinessBatchHandler(
                mock(SsoClientIdentityAdapter.class), preparationService, gateway);

        SsoMessageResult<SsoClientUserReadinessBatchResult> result = handler.handle(payload());

        assertSame(expected, result.data());
        verify(gateway).fetchUserMetas(ids);
        verify(preparationService).checkUsers(users, SsoClientGrantPurpose.ADMIN_GRANT);
    }

    @Test
    void preparationMustResolveOneTrustedMetadataBatchBeforeCallingHostSpi() {
        List<Long> ids = List.of(1001L, 1002L);
        List<SsoUserMeta> users = List.of(meta(1001L), meta(1002L));
        SsoClientUserPreparationBatchResult expected =
                SsoClientUserPreparationBatchResult.builder().submittedCount(2).build();
        when(gateway.fetchUserMetas(ids)).thenReturn(users);
        when(preparationService.prepareUsers(users, SsoClientGrantPurpose.ADMIN_GRANT))
                .thenReturn(expected);
        SsoClientUserPreparationBatchHandler handler =
                new SsoClientUserPreparationBatchHandler(preparationService, gateway);

        SsoMessageResult<SsoClientUserPreparationBatchResult> result = handler.handle(payload());

        assertSame(expected, result.data());
        verify(gateway).fetchUserMetas(ids);
        verify(preparationService).prepareUsers(users, SsoClientGrantPurpose.ADMIN_GRANT);
    }

    private static Map<String, Object> payload() {
        return Map.of(
                SsoProtocolFields.CENTER_IDS, "1001,1002",
                SsoProtocolFields.PURPOSE, SsoClientGrantPurpose.ADMIN_GRANT.name());
    }

    private static SsoUserMeta meta(long id) {
        return SsoUserMeta.builder().ssoUserId(id).username("user_" + id).build();
    }
}
