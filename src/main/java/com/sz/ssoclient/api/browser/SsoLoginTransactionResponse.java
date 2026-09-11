package com.sz.ssoclient.api.browser;

/** 创建 Browser 登录事务后的导航结果。 */
public record SsoLoginTransactionResponse(String authorizationUrl) {

    public SsoLoginTransactionResponse {
        if (authorizationUrl == null || authorizationUrl.isBlank()) {
            throw new IllegalArgumentException("authorizationUrl must not be blank");
        }
    }
}
