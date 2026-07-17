package com.sz.ssoclient.clientaccess.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sz.ssocore.clientaccess.v1.dto.LoginAuthorizationRequest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAccessTransportRetryTest {

    @Test
    void retriesConnectionFailureWithSameIdempotencyKey() {
        AtomicInteger calls = new AtomicInteger();
        ClientAccessHttpTransport transport = request -> {
            if (calls.incrementAndGet() == 1) {
                throw new ClientAccessTransportException("connection reset", new IOException("reset"));
            }
            String body = "{\"success\":true,\"code\":\"CAP-0000\",\"message\":\"success\","
                    + "\"requestId\":\"request\",\"timestamp\":\"2026-07-13T03:00:00Z\","
                    + "\"data\":{\"authorizationRequestId\":\"auth-1\","
                    + "\"authorizationUrl\":\"https://auth.example.com/login\","
                    + "\"expiresAt\":\"2026-07-13T03:10:00Z\"},\"details\":[]}";
            return new ClientAccessHttpResponse(201, body.getBytes(StandardCharsets.UTF_8));
        };
        ClientAccessRegistration registration = new ClientAccessRegistration(
                URI.create("https://sso.example.com"), "client-a",
                "secret".getBytes(StandardCharsets.UTF_8));
        AtomicInteger generated = new AtomicInteger();
        ClientAccessV1Client client = new ClientAccessV1Client(new ObjectMapper().findAndRegisterModules(),
                () -> registration, transport, Clock.fixed(Instant.parse("2026-07-13T03:00:00Z"), ZoneOffset.UTC),
                () -> "request-" + generated.incrementAndGet(),
                () -> "0123456789abcdef0123456789abcdef", 2);

        client.authorize(new LoginAuthorizationRequest(
                "0123456789abcdef0123456789abcdef", null, null), "idempotency-1");

        assertThat(calls).hasValue(2);
    }
}
