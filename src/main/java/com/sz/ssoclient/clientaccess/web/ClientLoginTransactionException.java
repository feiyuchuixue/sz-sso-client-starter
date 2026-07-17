package com.sz.ssoclient.clientaccess.web;

/** Stable browser-contract error raised by the Client backend. */
public class ClientLoginTransactionException extends RuntimeException {

    private final String code;

    public ClientLoginTransactionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
