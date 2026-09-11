package com.sz.ssoclient.internal.messaging;

import com.sz.ssoclient.api.SsoClientSuperAdminSyncOperations;
import com.sz.ssoclient.spi.SsoClientIdentityAdapter;
import com.sz.ssocore.SsoMessageResult;
import com.sz.ssocore.SsoMessageTypes;
import com.sz.ssocore.SsoProtocolFields;
import com.sz.ssocore.SsoUserMeta;
import com.sz.ssocore.SsoUserMetaUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Starter 固定内核使用的类型化 Client → Server Gateway。 */
public class SsoClientMessageGateway implements SsoClientSuperAdminSyncOperations {

    private static final Set<String> CLIENT_TO_SERVER_TYPES = Set.of(
            SsoMessageTypes.USER_CHECK,
            SsoMessageTypes.USER_CHECK_BATCH,
            SsoMessageTypes.QUERY_CLIENT_SUPER_ADMIN_STATUS,
            SsoMessageTypes.SYNC_SUPER_ADMIN,
            SsoMessageTypes.CREATE_PORTAL_TICKET,
            SsoMessageTypes.REQUEST_SLO);

    private final SaTokenSsoMessageTransport transport;
    private final SsoClientIdentityAdapter identityAdapter;

    public SsoClientMessageGateway(
            SaTokenSsoMessageTransport transport,
            SsoClientIdentityAdapter identityAdapter) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.identityAdapter = Objects.requireNonNull(identityAdapter, "identityAdapter");
    }

    @Override
    public SsoMessageResult<Void> syncSuperAdmin(String localUserId, boolean superAdmin) {
        if (localUserId == null || localUserId.isBlank()) {
            throw new IllegalArgumentException("localUserId 不能为空");
        }
        Optional<Long> ssoUserId = identityAdapter.findSsoUserId(localUserId);
        if (ssoUserId == null || ssoUserId.isEmpty()) {
            return new SsoMessageResult<>(
                    "SSO_USER_MAPPING_NOT_FOUND",
                    "本地用户不存在可信 SSO 映射",
                    null);
        }
        return send(
                SsoMessageTypes.SYNC_SUPER_ADMIN,
                Map.of(
                        SsoProtocolFields.SSO_USER_ID, ssoUserId.get(),
                        SsoProtocolFields.IS_SUPER_ADMIN, superAdmin),
                Void.class);
    }

    public <T> SsoMessageResult<T> send(
            String messageType,
            Map<String, ?> payload,
            Class<T> responseType) {
        if (!CLIENT_TO_SERVER_TYPES.contains(messageType)) {
            throw new IllegalArgumentException("Starter 固定 Gateway 不允许该消息方向或类型: " + messageType);
        }
        return transport.sendTrusted(messageType, payload, responseType);
    }

    /** 固定 Handler 通过可信 Client -> Server Core 消息批量读取用户快照。 */
    public List<SsoUserMeta> fetchUserMetas(Collection<Long> ssoUserIds) {
        Objects.requireNonNull(ssoUserIds, "ssoUserIds");
        List<Long> requestedIds = List.copyOf(ssoUserIds);
        if (requestedIds.isEmpty() || requestedIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("SSO 用户 ID 集合不能为空且不能包含空值");
        }
        String centerIds = requestedIds.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        SsoMessageResult<Map> result = send(
                SsoMessageTypes.USER_CHECK_BATCH,
                Map.of(SsoProtocolFields.CENTER_IDS, centerIds),
                Map.class);
        if (result == null || !result.successful() || result.data() == null) {
            throw new IllegalStateException(result == null || result.message() == null
                    ? "SSO Server 批量用户身份查询失败"
                    : result.message());
        }
        Object rawUsers = result.data().get("users");
        if (!(rawUsers instanceof Iterable<?> users)) {
            throw new IllegalStateException("SSO Server 批量用户身份响应缺少 users");
        }
        Map<Long, SsoUserMeta> usersById = new LinkedHashMap<>();
        for (Object rawUser : users) {
            if (!(rawUser instanceof Map<?, ?> rawMap)) {
                throw new IllegalStateException("SSO Server 批量用户身份响应包含非法用户项");
            }
            Map<String, Object> userMap = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> {
                if (key != null) {
                    userMap.put(key.toString(), value);
                }
            });
            SsoUserMeta meta = SsoUserMetaUtils.fromMap(userMap);
            if (meta == null || meta.getSsoUserId() == null
                    || usersById.putIfAbsent(meta.getSsoUserId(), meta) != null) {
                throw new IllegalStateException("SSO Server 批量用户身份响应包含空值或重复用户");
            }
        }
        List<SsoUserMeta> ordered = new ArrayList<>(requestedIds.size());
        for (Long requestedId : requestedIds) {
            SsoUserMeta meta = usersById.get(requestedId);
            if (meta == null) {
                throw new IllegalStateException("SSO Server 批量用户身份响应缺少请求用户");
            }
            ordered.add(meta);
        }
        return List.copyOf(ordered);
    }
}
