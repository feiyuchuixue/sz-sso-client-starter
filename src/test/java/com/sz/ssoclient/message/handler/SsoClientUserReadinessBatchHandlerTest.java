package com.sz.ssoclient.message.handler;

import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import com.sz.ssoclient.spi.SsoClientUserProvisioningService;
import com.sz.ssoclient.autoconfigure.SsoClientMessageAutoConfiguration;
import com.sz.ssoclient.spi.SsoUserMappingService;
import com.sz.ssocore.SsoClientSyncFailureCodes;
import com.sz.ssocore.provisioning.SsoClientGrantPurpose;
import com.sz.ssocore.provisioning.SsoClientUserReadinessBatchResult;
import com.sz.ssocore.provisioning.SsoClientUserReadinessStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SsoClientUserReadinessBatchHandlerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SsoClientMessageAutoConfiguration.class))
            .withBean(SaSsoClientTemplate.class, SaSsoClientTemplate::new)
            .withBean(SsoUserMappingService.class, RecordingMappingService::new);

    @Test
    void autoConfigurationStartsWithoutProvisioningSpi() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SsoClientUserReadinessBatchHandler.class);
            assertThat(context).doesNotHaveBean(SsoClientUserProvisioningService.class);
        });
    }

    @Test
    void fallbackReturnsReadyForExistingMappingWithoutProvisioning() {
        RecordingMappingService mappingService = new RecordingMappingService(Map.of(101L, 1001L));
        SsoClientUserReadinessBatchHandler handler = new SsoClientUserReadinessBatchHandler(mappingService, null);

        SsoClientUserReadinessBatchResult result = resultOf(handler.handle(null, message(List.of(101L))));

        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getSsoUserId()).isEqualTo(101L);
            assertThat(item.getLocalUserId()).isEqualTo(1001L);
            assertThat(item.getStatus()).isEqualTo(SsoClientUserReadinessStatus.READY);
        });
    }

    @Test
    void fallbackReturnsUnsupportedForUnmappedUserWithoutProvisioning() {
        SsoClientUserReadinessBatchHandler handler = new SsoClientUserReadinessBatchHandler(new RecordingMappingService(), null);

        SsoClientUserReadinessBatchResult result = resultOf(handler.handle(null, message(List.of(102L))));

        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getStatus()).isEqualTo(SsoClientUserReadinessStatus.UNSUPPORTED);
            assertThat(item.getReasonCode()).isEqualTo(SsoClientSyncFailureCodes.CLIENT_USER_PROVISION_UNSUPPORTED);
        });
    }

    @Test
    void fallbackReadinessNeverInvokesProvisioningMethod() {
        RecordingMappingService mappingService = new RecordingMappingService(Map.of(103L, 1003L));
        SsoClientUserReadinessBatchHandler handler = new SsoClientUserReadinessBatchHandler(mappingService, null);

        handler.handle(null, message(List.of(103L)));

        assertThat(mappingService.resolveOrProvisionCalls).isZero();
    }

    @Test
    void spiExceptionBecomesStableReadinessFailureCode() {
        SsoClientUserProvisioningService provisioningService = new SsoClientUserProvisioningService() {
            @Override
            public SsoClientUserReadinessBatchResult checkUsers(Collection<?> centerIds, SsoClientGrantPurpose purpose) {
                throw new IllegalStateException("client unavailable");
            }

            @Override
            public com.sz.ssocore.provisioning.SsoClientUserPreparationBatchResult prepareUsers(
                    Collection<?> centerIds, SsoClientGrantPurpose purpose) {
                throw new UnsupportedOperationException();
            }
        };
        SsoClientUserReadinessBatchHandler handler = new SsoClientUserReadinessBatchHandler(
                new RecordingMappingService(), provisioningService);

        SsoClientUserReadinessBatchResult result = resultOf(handler.handle(null, message(List.of(104L))));

        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getStatus()).isEqualTo(SsoClientUserReadinessStatus.CHECK_FAILED);
            assertThat(item.getReasonCode()).isEqualTo(SsoClientSyncFailureCodes.CLIENT_USER_READINESS_CHECK_FAILED);
        });
    }

    private static SaSsoMessage message(List<Long> centerIds) {
        SaSsoMessage message = new SaSsoMessage();
        message.set("centerIds", centerIds);
        message.set("purpose", SsoClientGrantPurpose.ADMIN_GRANT.name());
        return message;
    }

    private static SsoClientUserReadinessBatchResult resultOf(cn.dev33.satoken.util.SaResult result) {
        return (SsoClientUserReadinessBatchResult) result.getData();
    }

    private static final class RecordingMappingService implements SsoUserMappingService {

        private final Map<Object, Object> mappings;
        private int resolveOrProvisionCalls;

        private RecordingMappingService() {
            this(new LinkedHashMap<>());
        }

        private RecordingMappingService(Map<Object, Object> mappings) {
            this.mappings = new LinkedHashMap<>(mappings);
        }

        @Override
        public Object toServerUserId(Object clientUserId) {
            return null;
        }

        @Override
        public Object toClientUserId(Object serverUserId) {
            return mappings.get(serverUserId);
        }

        @Override
        public Object resolveOrProvisionClientUser(Object serverUserId) {
            resolveOrProvisionCalls++;
            throw new AssertionError("readiness must not provision users");
        }

        @Override
        public void syncSsoRegisterUser(SaSsoMessage message, String client) {
        }
    }
}
