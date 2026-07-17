package com.sz.ssoclient.clientaccess.inbound;

import com.sz.ssoclient.clientaccess.http.ClientAccessRegistration;
import com.sz.ssoclient.clientaccess.http.ClientAccessRegistrationProvider;
import com.sz.ssocore.clientaccess.v1.ClientAccessDirection;
import com.sz.ssocore.clientaccess.v1.ClientAccessHeaders;
import com.sz.ssocore.clientaccess.v1.ClientAccessNonceKey;
import com.sz.ssocore.clientaccess.v1.ClientAccessNonceStore;
import com.sz.ssocore.clientaccess.v1.ClientAccessOperation;
import com.sz.ssocore.clientaccess.v1.ClientAccessSignatureInput;
import com.sz.ssocore.clientaccess.v1.ClientAccessSigner;
import com.sz.ssocore.clientaccess.v1.ClientAccessV1;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;

/** Verifies the exact raw body before claiming a Server-to-Client nonce. */
public class ClientAccessInboundVerifier {

    private final ClientAccessRegistrationProvider registrationProvider;
    private final ClientAccessNonceStore nonceStore;
    private final Clock clock;
    private final int maxClockSkewSeconds;
    private final int nonceTtlSeconds;

    public ClientAccessInboundVerifier(ClientAccessRegistrationProvider registrationProvider,
            ClientAccessNonceStore nonceStore, Clock clock, int maxClockSkewSeconds, int nonceTtlSeconds) {
        this.registrationProvider = Objects.requireNonNull(registrationProvider, "registrationProvider");
        this.nonceStore = Objects.requireNonNull(nonceStore, "nonceStore");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (maxClockSkewSeconds <= 0 || nonceTtlSeconds <= 0) {
            throw new IllegalArgumentException("CAP inbound security windows must be positive");
        }
        this.maxClockSkewSeconds = maxClockSkewSeconds;
        this.nonceTtlSeconds = nonceTtlSeconds;
    }

    public void verify(ClientAccessOperation expectedOperation, String method, String path,
            Map<String, String> headers, byte[] rawBody) {
        if (expectedOperation.direction() != ClientAccessDirection.SERVER_TO_CLIENT
                || !expectedOperation.httpMethod().equals(method)
                || !expectedOperation.logicalPath().equals(path)) {
            throw failure("CAP-1001", 400, "CAP operation, method or path mismatch");
        }
        ClientAccessRegistration registration = registrationProvider.current();
        String version = header(headers, ClientAccessHeaders.PROTOCOL_VERSION);
        if (!ClientAccessV1.VERSION.equals(version)) {
            throw failure("CAP-1002", 400, "Unsupported CAP protocol version");
        }
        if (!ClientAccessDirection.SERVER_TO_CLIENT.name().equals(header(headers, ClientAccessHeaders.DIRECTION))
                || !registration.clientFlag().equals(header(headers, ClientAccessHeaders.CLIENT))) {
            throw failure("CAP-2001", 401, "CAP Client authentication failed");
        }
        if (!expectedOperation.name().equals(header(headers, ClientAccessHeaders.OPERATION))) {
            throw failure("CAP-1001", 400, "CAP operation header mismatch");
        }
        String actualBodyHash = ClientAccessSigner.bodySha256(rawBody == null ? new byte[0] : rawBody);
        if (!actualBodyHash.equals(header(headers, ClientAccessHeaders.BODY_SHA256))) {
            throw failure("CAP-2007", 401, "CAP body hash mismatch");
        }
        ClientAccessSignatureInput input;
        try {
            input = new ClientAccessSignatureInput(version, ClientAccessDirection.SERVER_TO_CLIENT,
                    registration.clientFlag(), header(headers, ClientAccessHeaders.REQUEST_ID),
                    expectedOperation, header(headers, ClientAccessHeaders.IDEMPOTENCY_KEY),
                    header(headers, ClientAccessHeaders.TIMESTAMP), header(headers, ClientAccessHeaders.NONCE),
                    actualBodyHash);
        } catch (IllegalArgumentException exception) {
            throw failure("CAP-1001", 400, "CAP signature headers are invalid");
        }
        if (!ClientAccessSigner.verify(input, registration.secret(), header(headers, ClientAccessHeaders.SIGNATURE))) {
            throw failure("CAP-2003", 401, "CAP signature is invalid");
        }
        Instant timestamp;
        try {
            timestamp = Instant.parse(input.timestamp());
        } catch (DateTimeParseException exception) {
            throw failure("CAP-2004", 401, "CAP timestamp is invalid");
        }
        Instant now = clock.instant();
        if (Math.abs(Duration.between(timestamp, now).toSeconds()) > maxClockSkewSeconds) {
            throw failure("CAP-2004", 401, "CAP timestamp is outside the accepted window");
        }
        ClientAccessNonceKey nonceKey = new ClientAccessNonceKey(ClientAccessV1.VERSION,
                ClientAccessDirection.SERVER_TO_CLIENT, registration.clientFlag(), input.nonce());
        if (!nonceStore.tryClaim(nonceKey, now.plusSeconds(nonceTtlSeconds))) {
            throw failure("CAP-2005", 401, "CAP nonce was already used");
        }
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (name.equalsIgnoreCase(entry.getKey()) && entry.getValue() != null && !entry.getValue().isBlank()) {
                    return entry.getValue();
                }
            }
        }
        throw failure("CAP-1001", 400, "Missing CAP header: " + name);
    }

    private static ClientAccessInboundException failure(String code, int status, String message) {
        return new ClientAccessInboundException(code, status, message);
    }
}
