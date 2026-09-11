package com.sz.ssoclient.spi.advanced;

import com.sz.ssoclient.spi.SsoClientSessionHandle;

/** 当前可信 Client 本地会话的中立快照。 */
public record SsoClientLocalSession(
        boolean authenticated,
        String localUserId,
        String deviceId,
        SsoClientSessionHandle sessionHandle) {

    public SsoClientLocalSession {
        if (authenticated) {
            requireText(localUserId, "localUserId");
            requireText(deviceId, "deviceId");
            if (sessionHandle == null) {
                throw new IllegalArgumentException("authenticated session requires sessionHandle");
            }
        } else if (localUserId != null || deviceId != null || sessionHandle != null) {
            throw new IllegalArgumentException("unauthenticated session must not carry trusted identity fields");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
