package com.sz.ssoclient.clientaccess.web;

import java.time.Instant;

/** Current Client-local authentication state used inside the confidential backend. */
public record ClientLocalSession(
        boolean authenticated, Object localUserId, String deviceId, Instant expireAt) {
    public static ClientLocalSession anonymous() {
        return new ClientLocalSession(false, null, null, null);
    }
}
