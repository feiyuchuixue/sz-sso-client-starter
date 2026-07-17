package com.sz.ssoclient.clientaccess.http;

import com.sz.ssocore.clientaccess.v1.dto.ClientAccessResponse;

/** Stable CAP failure returned by the remote Server. */
public class ClientAccessRemoteException extends RuntimeException {

    private final int httpStatus;
    private final ClientAccessResponse<?> response;

    public ClientAccessRemoteException(int httpStatus, ClientAccessResponse<?> response) {
        this(httpStatus, response, null);
    }

    public ClientAccessRemoteException(int httpStatus, ClientAccessResponse<?> response, Throwable cause) {
        super(response == null ? "Client Access request failed" : response.message(), cause);
        this.httpStatus = httpStatus;
        this.response = response;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public ClientAccessResponse<?> getResponse() {
        return response;
    }
}
