package com.sz.ssoclient.clientaccess.inbound;

import com.sz.ssoclient.clientaccess.http.ClientAccessRegistration;
import com.sz.ssocore.clientaccess.v1.ClientAccessDirection;
import com.sz.ssocore.clientaccess.v1.ClientAccessHeaders;
import com.sz.ssocore.clientaccess.v1.ClientAccessOperation;
import com.sz.ssocore.clientaccess.v1.ClientAccessSignatureInput;
import com.sz.ssocore.clientaccess.v1.ClientAccessSigner;
import com.sz.ssocore.clientaccess.v1.ClientAccessV1;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientAccessInboundVerifierTest {

    private static final Instant NOW = Instant.parse("2026-07-13T03:00:00Z");
    private static final byte[] SECRET = "client-secret".getBytes(StandardCharsets.UTF_8);

    @Test
    void verifiesServerDirectionThenClaimsNonceExactlyOnce() {
        ClientAccessRegistration registration = new ClientAccessRegistration(
                URI.create("https://sso.example.com"), "client-a", SECRET);
        ClientAccessInboundVerifier verifier = new ClientAccessInboundVerifier(() -> registration,
                new InMemoryClientAccessNonceStore(Clock.fixed(NOW, ZoneOffset.UTC)), Clock.fixed(NOW, ZoneOffset.UTC), 300, 600);
        byte[] body = "{\"eventId\":\"event-1\"}".getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = signedHeaders(ClientAccessOperation.SLO_CALLBACK, body);

        verifier.verify(ClientAccessOperation.SLO_CALLBACK, "POST", "/sso/v1/callbacks/logout", headers, body);

        assertThatThrownBy(() -> verifier.verify(ClientAccessOperation.SLO_CALLBACK, "POST",
                "/sso/v1/callbacks/logout", headers, body))
                .isInstanceOf(ClientAccessInboundException.class)
                .extracting("code")
                .isEqualTo("CAP-2005");
    }

    @Test
    void invalidSignatureDoesNotPoisonNonce() {
        ClientAccessRegistration registration = new ClientAccessRegistration(
                URI.create("https://sso.example.com"), "client-a", SECRET);
        ClientAccessInboundVerifier verifier = new ClientAccessInboundVerifier(() -> registration,
                new InMemoryClientAccessNonceStore(Clock.fixed(NOW, ZoneOffset.UTC)), Clock.fixed(NOW, ZoneOffset.UTC), 300, 600);
        byte[] body = "{\"eventId\":\"event-1\"}".getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = signedHeaders(ClientAccessOperation.SLO_CALLBACK, body);
        Map<String, String> invalid = new LinkedHashMap<>(headers);
        invalid.put(ClientAccessHeaders.SIGNATURE, "0".repeat(64));

        assertThatThrownBy(() -> verifier.verify(ClientAccessOperation.SLO_CALLBACK, "POST",
                "/sso/v1/callbacks/logout", invalid, body))
                .isInstanceOf(ClientAccessInboundException.class)
                .extracting("code")
                .isEqualTo("CAP-2003");
        verifier.verify(ClientAccessOperation.SLO_CALLBACK, "POST", "/sso/v1/callbacks/logout", headers, body);
    }

    private static Map<String, String> signedHeaders(ClientAccessOperation operation, byte[] body) {
        String bodyHash = ClientAccessSigner.bodySha256(body);
        ClientAccessSignatureInput input = new ClientAccessSignatureInput(ClientAccessV1.VERSION,
                ClientAccessDirection.SERVER_TO_CLIENT, "client-a", "request-1", operation,
                "idempotency-1", NOW.toString(), "0123456789abcdef0123456789abcdef", bodyHash);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(ClientAccessHeaders.PROTOCOL_VERSION, input.protocolVersion());
        headers.put(ClientAccessHeaders.DIRECTION, input.direction().name());
        headers.put(ClientAccessHeaders.CLIENT, input.clientFlag());
        headers.put(ClientAccessHeaders.REQUEST_ID, input.requestId());
        headers.put(ClientAccessHeaders.OPERATION, input.operation().name());
        headers.put(ClientAccessHeaders.IDEMPOTENCY_KEY, input.idempotencyKey());
        headers.put(ClientAccessHeaders.TIMESTAMP, input.timestamp());
        headers.put(ClientAccessHeaders.NONCE, input.nonce());
        headers.put(ClientAccessHeaders.BODY_SHA256, input.bodySha256());
        headers.put(ClientAccessHeaders.SIGNATURE, ClientAccessSigner.sign(input, SECRET));
        return headers;
    }
}
