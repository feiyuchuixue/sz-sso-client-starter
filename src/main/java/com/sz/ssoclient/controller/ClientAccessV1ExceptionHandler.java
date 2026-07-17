package com.sz.ssoclient.controller;

import com.sz.ssoclient.clientaccess.http.ClientAccessRemoteException;
import com.sz.ssoclient.clientaccess.inbound.ClientAccessInboundException;
import com.sz.ssoclient.clientaccess.web.ClientAccessWebException;
import com.sz.ssoclient.clientaccess.web.ClientLoginTransactionException;
import com.sz.ssocore.clientaccess.v1.dto.ClientAccessResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Stable error mapping scoped to CAP V1 browser endpoints. */
@RestControllerAdvice(assignableTypes = {ClientAccessV1Controller.class, ClientAccessInboundController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ClientAccessV1ExceptionHandler {

    @ExceptionHandler(ClientAccessRemoteException.class)
    public ResponseEntity<ClientAccessResponse<?>> remote(ClientAccessRemoteException exception) {
        return ResponseEntity.status(exception.getHttpStatus()).body(exception.getResponse());
    }

    @ExceptionHandler(ClientLoginTransactionException.class)
    public ResponseEntity<ClientAccessResponse<Void>> transaction(ClientLoginTransactionException exception) {
        return failure(400, exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(ClientAccessWebException.class)
    public ResponseEntity<ClientAccessResponse<Void>> web(ClientAccessWebException exception) {
        return failure(exception.getHttpStatus(), exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(ClientAccessInboundException.class)
    public ResponseEntity<ClientAccessResponse<Void>> inbound(ClientAccessInboundException exception) {
        return failure(exception.getHttpStatus(), exception.getCode(), exception.getMessage());
    }

    private static ResponseEntity<ClientAccessResponse<Void>> failure(int status, String code, String message) {
        ClientAccessResponse<Void> body = new ClientAccessResponse<>(false, code, message,
                UUID.randomUUID().toString().replace("-", ""), Instant.now(), null, List.of());
        return ResponseEntity.status(status).body(body);
    }
}
