package com.sz.ssoclient.spi;

import cn.dev33.satoken.sso.message.SaSsoMessage;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SSO 用户 ID 映射 SPI.
 */
public interface SsoUserMappingService {

    Object toServerUserId(Object clientUserId);

    /**
     * 将 SSO Server 用户 ID 转换为 Client 本地用户 ID。
     * 该方法只表达 ID 映射查询语义，不应创建 Client 本地用户。
     */
    Object toClientUserId(Object serverUserId);

    /**
     * 解析已有 Client 本地用户。
     * 可在缺 mapping 时由 Client Adapter 尝试匹配已有用户并补 mapping，但不应创建用户。
     */
    default Object resolveExistingClientUser(Object serverUserId) {
        return toClientUserId(serverUserId);
    }

    /**
     * 批量解析已有 Client 本地用户。
     * 默认实现逐条调用 {@link #resolveExistingClientUser(Object)}，Client Adapter 可覆盖为批量 SQL/批量远程查询。
     */
    default Map<Object, Object> resolveExistingClientUsers(Collection<?> serverUserIds) {
        Map<Object, Object> result = new LinkedHashMap<>();
        if (serverUserIds == null || serverUserIds.isEmpty()) {
            return result;
        }
        for (Object serverUserId : serverUserIds) {
            if (serverUserId == null) {
                continue;
            }
            Object clientUserId = resolveExistingClientUser(serverUserId);
            if (clientUserId != null) {
                result.put(serverUserId, clientUserId);
            }
        }
        return result;
    }

    /**
     * 登录链路解析 Client 本地用户。
     * Client Adapter 可在补 mapping 失败且业务允许时创建本地用户。
     */
    default Object resolveOrProvisionClientUser(Object serverUserId) {
        return toClientUserId(serverUserId);
    }

    void syncSsoRegisterUser(SaSsoMessage message, String client);
}