package com.sz.ssoclient.internal.web;

/** Browser HTTP 边界内部使用的稳定错误。 */
public final class SsoClientWebException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public SsoClientWebException(String code, int httpStatus, String safeMessage) {
        super(safeMessage);
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (httpStatus < 400 || httpStatus > 599) {
            throw new IllegalArgumentException("httpStatus must be an error status");
        }
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
