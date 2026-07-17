package com.sz.ssoclient.clientaccess.http;

/** Retryable network failure before a stable CAP response is available. */
public class ClientAccessTransportException extends RuntimeException {
    public ClientAccessTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
