package com.sz.ssoclient.clientaccess.web;

/** Stable local authorization or session failure for the Web Contract. */
public class ClientAccessWebException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public ClientAccessWebException(String code, int httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() { return code; }
    public int getHttpStatus() { return httpStatus; }
}
