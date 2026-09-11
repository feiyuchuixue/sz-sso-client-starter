package com.sz.ssoclient.api.browser;

/** Browser HTTP 稳定响应信封。 */
public record SsoWebResult<T>(String code, String message, T data) {

    public SsoWebResult {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }

    public static <T> SsoWebResult<T> success(T data) {
        return new SsoWebResult<>(SsoWebCodes.SUCCESS, "success", data);
    }

    public static <T> SsoWebResult<T> failure(String code, String message) {
        return new SsoWebResult<>(code, message, null);
    }
}
