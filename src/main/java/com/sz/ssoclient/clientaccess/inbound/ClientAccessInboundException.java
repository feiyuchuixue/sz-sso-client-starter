package com.sz.ssoclient.clientaccess.inbound;

/** Stable CAP authentication failure for Server-to-Client calls. */
public class ClientAccessInboundException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public ClientAccessInboundException(String code, int httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() { return code; }
    public int getHttpStatus() { return httpStatus; }
}
