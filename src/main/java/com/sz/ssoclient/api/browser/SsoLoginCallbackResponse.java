package com.sz.ssoclient.api.browser;

/** Browser 登录成功后唯一返回给宿主的字段。 */
public record SsoLoginCallbackResponse(String accessToken, String back) {

    public SsoLoginCallbackResponse {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken must not be blank");
        }
        if (back == null || back.isBlank()) {
            throw new IllegalArgumentException("back must not be blank");
        }
    }
}
