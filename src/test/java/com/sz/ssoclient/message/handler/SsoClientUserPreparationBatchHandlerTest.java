package com.sz.ssoclient.message.handler;

import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.util.SaResult;
import com.sz.ssoclient.spi.SsoClientUserProvisioningService;
import com.sz.ssocore.SsoClientSyncFailureCodes;
import com.sz.ssocore.provisioning.SsoClientGrantPurpose;
import com.sz.ssocore.provisioning.SsoClientUserPreparationBatchResult;
import com.sz.ssocore.provisioning.SsoClientUserPreparationItem;
import com.sz.ssocore.provisioning.SsoClientUserPreparationStatus;
import com.sz.ssocore.provisioning.SsoClientUserReadinessBatchResult;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SsoClientUserPreparationBatchHandlerTest {

    @Test
    void prepareKeepsInputOrderWhenOneSpiItemFails() {
        SsoClientUserProvisioningService provisioningService = new SsoClientUserProvisioningService() {
            @Override
            public SsoClientUserReadinessBatchResult checkUsers(Collection<?> centerIds, SsoClientGrantPurpose purpose) {
                throw new UnsupportedOperationException();
            }

            @Override
            public SsoClientUserPreparationBatchResult prepareUsers(Collection<?> centerIds, SsoClientGrantPurpose purpose) {
                Long ssoUserId = Long.valueOf(centerIds.iterator().next().toString());
                if (ssoUserId.equals(202L)) {
                    throw new IllegalStateException("local create failed");
                }
                return SsoClientUserPreparationBatchResult.builder()
                        .submittedCount(1)
                        .preparedCount(1)
                        .items(List.of(SsoClientUserPreparationItem.builder()
                                .ssoUserId(ssoUserId)
                                .localUserId(2001L)
                                .status(SsoClientUserPreparationStatus.PREPARED)
                                .build()))
                        .build();
            }
        };
        SsoClientUserPreparationBatchHandler handler = new SsoClientUserPreparationBatchHandler(provisioningService);

        SsoClientUserPreparationBatchResult result = resultOf(handler.handle(null, message(List.of(201L, 202L))));

        assertThat(result.getSubmittedCount()).isEqualTo(2);
        assertThat(result.getPreparedCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getItems()).extracting(SsoClientUserPreparationItem::getSsoUserId)
                .containsExactly(201L, 202L);
        assertThat(result.getItems().get(1).getStatus()).isEqualTo(SsoClientUserPreparationStatus.FAILED);
        assertThat(result.getItems().get(1).getReasonCode()).isEqualTo(SsoClientSyncFailureCodes.CLIENT_USER_PREPARE_FAILED);
    }

    private static SaSsoMessage message(List<Long> centerIds) {
        SaSsoMessage message = new SaSsoMessage();
        message.set("centerIds", centerIds);
        message.set("purpose", SsoClientGrantPurpose.ADMIN_GRANT.name());
        return message;
    }

    private static SsoClientUserPreparationBatchResult resultOf(SaResult result) {
        return (SsoClientUserPreparationBatchResult) result.getData();
    }
}
