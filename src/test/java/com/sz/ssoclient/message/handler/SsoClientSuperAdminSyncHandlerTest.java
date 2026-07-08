package com.sz.ssoclient.message.handler;

import cn.dev33.satoken.sso.message.SaSsoMessage;
import cn.dev33.satoken.util.SaResult;
import com.sz.ssoclient.spi.SsoRoleBindingService;
import com.sz.ssoclient.spi.SsoUserMappingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SsoClientSuperAdminSyncHandler 单元测试")
class SsoClientSuperAdminSyncHandlerTest {

    @Test
    @DisplayName("handle() 应使用 resolveExistingClientUser 解析已有本地用户")
    void handleShouldUseResolveExistingClientUser() {
        RecordingMappingService mappingService = new RecordingMappingService(207L);
        RecordingRoleBindingService roleBindingService = new RecordingRoleBindingService();
        SsoClientSuperAdminSyncHandler handler = new SsoClientSuperAdminSyncHandler(mappingService, roleBindingService);

        SaSsoMessage message = new SaSsoMessage();
        message.set("centerId", 431L);
        message.set("clientId", "platform");
        message.set("isSuperAdmin", true);

        SaResult result = handler.handle(null, message);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(mappingService.resolveExistingCalled).isTrue();
        assertThat(mappingService.toClientCalled).isFalse();
        assertThat(roleBindingService.localUserId).isEqualTo(207L);
        assertThat(roleBindingService.superAdmin).isTrue();
    }

    @Test
    @DisplayName("handle() 在无法解析已有本地用户时应返回错误且不落库")
    void handleShouldReturnErrorWhenExistingUserCannotBeResolved() {
        RecordingMappingService mappingService = new RecordingMappingService(null);
        RecordingRoleBindingService roleBindingService = new RecordingRoleBindingService();
        SsoClientSuperAdminSyncHandler handler = new SsoClientSuperAdminSyncHandler(mappingService, roleBindingService);

        SaSsoMessage message = new SaSsoMessage();
        message.set("centerId", 431L);
        message.set("clientId", "platform");
        message.set("isSuperAdmin", true);

        SaResult result = handler.handle(null, message);

        assertThat(result.getCode()).isNotEqualTo(200);
        assertThat(mappingService.resolveExistingCalled).isTrue();
        assertThat(roleBindingService.called).isFalse();
    }

    @Test
    @DisplayName("batch handle() 应批量解析本地用户并返回逐条失败明细")
    @SuppressWarnings("unchecked")
    void batchHandleShouldResolveUsersAndReturnItemFailures() {
        RecordingMappingService mappingService = new RecordingMappingService(null);
        mappingService.batchResult.put("431", 207L);
        mappingService.batchResult.put("433", 209L);
        RecordingRoleBindingService roleBindingService = new RecordingRoleBindingService();
        roleBindingService.failLocalUserIds.add(209L);
        SsoClientSuperAdminBatchSyncHandler handler = new SsoClientSuperAdminBatchSyncHandler(mappingService, roleBindingService);

        SaSsoMessage message = new SaSsoMessage();
        message.set("centerIds", "431,432,433");
        message.set("clientId", "platform");
        message.set("isSuperAdmin", true);

        SaResult result = handler.handle(null, message);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(mappingService.resolveBatchCalled).isTrue();
        assertThat(mappingService.resolvedBatchIds).extracting(Object::toString).containsExactly("431", "432", "433");
        assertThat(roleBindingService.appliedUserIds).containsExactly(207L, 209L);

        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertThat(data.get("requested")).isEqualTo(3);
        assertThat(data.get("success")).isEqualTo(1);
        assertThat(data.get("failed")).isEqualTo(2);
        assertThat((List<String>) data.get("successIds")).containsExactly("431");
        List<Map<String, Object>> failItems = (List<Map<String, Object>>) data.get("failItems");
        assertThat(failItems).extracting(item -> item.get("index")).containsExactly(2, 3);
        assertThat(failItems).extracting(item -> item.get("centerId")).containsExactly("432", "433");
        assertThat(failItems).extracting(item -> item.get("reason"))
                .containsExactly("local user mapping not found", "apply super admin failed");
    }

    private static final class RecordingMappingService implements SsoUserMappingService {

        private final Object localUserId;
        private final Map<Object, Object> batchResult = new LinkedHashMap<>();
        private List<?> resolvedBatchIds = List.of();
        private boolean toClientCalled;
        private boolean resolveExistingCalled;
        private boolean resolveBatchCalled;

        private RecordingMappingService(Object localUserId) {
            this.localUserId = localUserId;
        }

        @Override
        public Object toServerUserId(Object clientUserId) {
            return null;
        }

        @Override
        public Object toClientUserId(Object serverUserId) {
            toClientCalled = true;
            return 999L;
        }

        @Override
        public Object resolveExistingClientUser(Object serverUserId) {
            resolveExistingCalled = true;
            return localUserId;
        }

        @Override
        public Map<Object, Object> resolveExistingClientUsers(Collection<?> serverUserIds) {
            resolveBatchCalled = true;
            resolvedBatchIds = List.copyOf(serverUserIds);
            return batchResult;
        }

        @Override
        public void syncSsoRegisterUser(SaSsoMessage message, String client) {
        }
    }

    private static final class RecordingRoleBindingService implements SsoRoleBindingService {

        private boolean called;
        private Long localUserId;
        private boolean superAdmin;
        private final List<Long> failLocalUserIds = new java.util.ArrayList<>();
        private final List<Long> appliedUserIds = new java.util.ArrayList<>();

        @Override
        public void applyDefaultRole(Long localUserId, String defaultRoleKey) {
        }

        @Override
        public void applySuperAdmin(Long localUserId, boolean isSuperAdmin) {
            called = true;
            this.localUserId = localUserId;
            this.superAdmin = isSuperAdmin;
            appliedUserIds.add(localUserId);
            if (failLocalUserIds.contains(localUserId)) {
                throw new IllegalStateException("apply failed");
            }
        }
    }
}