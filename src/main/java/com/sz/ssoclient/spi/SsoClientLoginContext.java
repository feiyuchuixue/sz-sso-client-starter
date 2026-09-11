package com.sz.ssoclient.spi;

/** Starter 交给宿主建立本地 Session 的可信登录上下文。 */
public record SsoClientLoginContext(
        long ssoUserId,
        String localUserId,
        String deviceId,
        boolean superAdmin) {

    public SsoClientLoginContext {
        requireText(localUserId, "localUserId");
        requireText(deviceId, "deviceId");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
