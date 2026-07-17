package com.sz.ssoclient.clientaccess.web;

import com.sz.ssoclient.pojo.SsoLoginResult;

import java.time.Instant;
import java.util.Objects;

/** Hashed, browser-bound state kept by the confidential Client backend. */
public record ClientLoginTransaction(
        String stateHash,
        String browserSessionHash,
        String clientFlag,
        String back,
        String authorizationRequestId,
        String exchangeIdempotencyKey,
        Instant createdAt,
        Instant expiresAt,
        ClientLoginTransactionStatus status,
        SsoLoginResult completedResult) {

    public ClientLoginTransaction {
        Objects.requireNonNull(stateHash, "stateHash");
        Objects.requireNonNull(browserSessionHash, "browserSessionHash");
        Objects.requireNonNull(clientFlag, "clientFlag");
        Objects.requireNonNull(back, "back");
        if (status == ClientLoginTransactionStatus.AUTHORIZING) {
            if (authorizationRequestId != null) {
                throw new IllegalArgumentException("authorizing transaction cannot have an authorizationRequestId");
            }
        } else {
            Objects.requireNonNull(authorizationRequestId, "authorizationRequestId");
        }
        Objects.requireNonNull(exchangeIdempotencyKey, "exchangeIdempotencyKey");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(status, "status");
        if (status == ClientLoginTransactionStatus.COMPLETED && completedResult == null) {
            throw new IllegalArgumentException("completed transaction requires a login result");
        }
    }

    public ClientLoginTransaction authorized(String requestId, Instant authorizationExpiresAt) {
        if (status != ClientLoginTransactionStatus.AUTHORIZING) {
            throw new IllegalStateException("Only an authorizing transaction can be authorized");
        }
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(authorizationExpiresAt, "authorizationExpiresAt");
        Instant effectiveExpiry = authorizationExpiresAt.isBefore(expiresAt) ? authorizationExpiresAt : expiresAt;
        if (!effectiveExpiry.isAfter(createdAt)) {
            throw new IllegalArgumentException("authorized transaction must expire after creation");
        }
        return new ClientLoginTransaction(stateHash, browserSessionHash, clientFlag, back,
                requestId, exchangeIdempotencyKey, createdAt, effectiveExpiry,
                ClientLoginTransactionStatus.CREATED, null);
    }

    public ClientLoginTransaction withStatus(ClientLoginTransactionStatus newStatus) {
        return new ClientLoginTransaction(stateHash, browserSessionHash, clientFlag, back,
                authorizationRequestId, exchangeIdempotencyKey, createdAt, expiresAt, newStatus,
                newStatus == ClientLoginTransactionStatus.COMPLETED ? completedResult : null);
    }

    public ClientLoginTransaction completed(SsoLoginResult result) {
        return new ClientLoginTransaction(stateHash, browserSessionHash, clientFlag, back,
                authorizationRequestId, exchangeIdempotencyKey, createdAt, expiresAt,
                ClientLoginTransactionStatus.COMPLETED, Objects.requireNonNull(result, "result"));
    }
}
