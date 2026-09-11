package com.sz.ssoclient.api.browser;

/** 已登录 Client 用户进入认证中心的单次 Portal 入口。 */
public record SsoPortalEntryResponse(String portalUrl, String targetPath) {

    public SsoPortalEntryResponse {
        if (portalUrl == null || portalUrl.isBlank()) {
            throw new IllegalArgumentException("portalUrl must not be blank");
        }
        if (targetPath == null || targetPath.isBlank()) {
            throw new IllegalArgumentException("targetPath must not be blank");
        }
    }
}
