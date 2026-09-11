package com.sz.ssoclient.internal.messaging.handler;

import com.sz.ssoclient.internal.messaging.SsoClientMessageRegistrar;
import com.sz.ssoclient.spi.SsoClientIdentityAdapter;
import com.sz.ssoclient.spi.SsoSuperAdminSnapshotProvider;
import com.sz.ssocore.SsoMessageResult;
import com.sz.ssocore.SsoMessageTypes;
import com.sz.ssocore.superadmin.SsoSuperAdminSnapshotResult;
import com.sz.ssocore.superadmin.SsoSuperAdminSnapshotStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Server 查询 Client 完整权威 SSO 超管快照的固定 Handler。 */
@Slf4j
public final class SsoClientSuperAdminSnapshotHandler
        implements SsoClientMessageRegistrar.FixedHandler {

    private final SsoClientIdentityAdapter identityAdapter;
    private final SsoSuperAdminSnapshotProvider snapshotProvider;

    public SsoClientSuperAdminSnapshotHandler(
            SsoClientIdentityAdapter identityAdapter,
            SsoSuperAdminSnapshotProvider snapshotProvider) {
        this.identityAdapter = Objects.requireNonNull(identityAdapter, "identityAdapter");
        this.snapshotProvider = snapshotProvider;
    }

    @Override
    public String messageType() {
        return SsoMessageTypes.QUERY_CLIENT_SUPER_ADMIN_SNAPSHOT;
    }

    @Override
    public SsoMessageResult<SsoSuperAdminSnapshotResult> handle(Map<String, Object> payload) {
        if (snapshotProvider == null) {
            return success(new SsoSuperAdminSnapshotResult(
                    SsoSuperAdminSnapshotStatus.UNSUPPORTED,
                    Set.of(),
                    null));
        }
        try {
            Collection<String> localUserIds = snapshotProvider.listAllSsoSuperAdminUserIds();
            if (localUserIds == null) {
                return failed("快照 Provider 返回了 null");
            }
            LinkedHashSet<String> uniqueLocalIds = new LinkedHashSet<>();
            for (String localUserId : localUserIds) {
                if (localUserId == null || localUserId.isBlank()) {
                    return failed("快照包含空白 localUserId");
                }
                if (!uniqueLocalIds.add(localUserId)) {
                    return failed("快照包含重复 localUserId");
                }
            }
            Map<String, Long> mappings = identityAdapter.findSsoUserIds(uniqueLocalIds);
            if (mappings == null || mappings.size() != uniqueLocalIds.size()
                    || !mappings.keySet().containsAll(uniqueLocalIds)
                    || mappings.values().stream().anyMatch(Objects::isNull)) {
                return failed("快照 localUserId 到 SSO 用户 ID 的映射不完整");
            }
            Set<Long> ssoUserIds = new HashSet<>(mappings.values());
            if (ssoUserIds.size() != mappings.size()) {
                return failed("多个快照 localUserId 映射到同一 SSO 用户 ID");
            }
            return success(new SsoSuperAdminSnapshotResult(
                    SsoSuperAdminSnapshotStatus.AUTHORITATIVE,
                    ssoUserIds,
                    null));
        } catch (RuntimeException exception) {
            log.warn("[SSO] 获取 Client SSO 超管权威快照失败", exception);
            return failed("获取 Client SSO 超管权威快照失败");
        }
    }

    private static SsoMessageResult<SsoSuperAdminSnapshotResult> failed(String reason) {
        return success(new SsoSuperAdminSnapshotResult(
                SsoSuperAdminSnapshotStatus.FAILED,
                Set.of(),
                reason));
    }

    private static SsoMessageResult<SsoSuperAdminSnapshotResult> success(SsoSuperAdminSnapshotResult data) {
        return new SsoMessageResult<>(SsoMessageResult.SUCCESS_CODE, "Client SSO 超管快照结果已形成", data);
    }
}
