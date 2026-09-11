package com.sz.ssoclient.spi;

/** 宿主完成本地 Session 建立后返回的最小登录结果。 */
public record SsoClientLoginResult(
        String accessToken,
        SsoClientSessionHandle sessionHandle) {

    public SsoClientLoginResult {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken must not be blank");
        }
        if (sessionHandle == null) {
            throw new IllegalArgumentException("sessionHandle must not be null");
        }
    }
}
