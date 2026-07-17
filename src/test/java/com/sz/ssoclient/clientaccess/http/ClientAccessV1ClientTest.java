package com.sz.ssoclient.clientaccess.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sz.ssocore.clientaccess.v1.ClientAccessDirection;
import com.sz.ssocore.clientaccess.v1.ClientAccessHeaders;
import com.sz.ssocore.clientaccess.v1.ClientAccessOperation;
import com.sz.ssocore.clientaccess.v1.ClientAccessSignatureInput;
import com.sz.ssocore.clientaccess.v1.ClientAccessSigner;
import com.sz.ssocore.clientaccess.v1.ClientAccessV1;
import com.sz.ssocore.clientaccess.v1.dto.LoginAuthorizationRequest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientAccessV1ClientTest {

    private static final byte[] SECRET = "client-secret-for-cap-v1".getBytes(StandardCharsets.UTF_8);

    @Test
    void authorizeSignsTheExactJsonBodyWithTheCoreContract() {
        RecordingTransport transport = new RecordingTransport(List.of(successAuthorization()));
        ClientAccessV1Client client = client(transport, () -> "request-001",
                () -> "0123456789abcdef0123456789abcdef");

        client.authorize(new LoginAuthorizationRequest(
                "abcdef0123456789abcdef0123456789", "default", "light"),
                "idempotency-001");

        ClientAccessHttpRequest request = transport.requests.get(0);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.uri()).isEqualTo(URI.create(
                "https://sso.example.com/api/sso/client-api/v1/login/authorizations"));
        assertThat(request.headers())
                .containsEntry(ClientAccessHeaders.PROTOCOL_VERSION, ClientAccessV1.VERSION)
                .containsEntry(ClientAccessHeaders.DIRECTION, ClientAccessDirection.CLIENT_TO_SERVER.name())
                .containsEntry(ClientAccessHeaders.CLIENT, "client-a")
                .doesNotContainKey("X-SZSso-Key-Id")
                .containsEntry(ClientAccessHeaders.REQUEST_ID, "request-001")
                .containsEntry(ClientAccessHeaders.OPERATION, ClientAccessOperation.LOGIN_AUTHORIZE.name())
                .containsEntry(ClientAccessHeaders.IDEMPOTENCY_KEY, "idempotency-001");

        assertThat(new String(request.body(), StandardCharsets.UTF_8)).doesNotContain("redirectUri");
        String bodyHash = ClientAccessSigner.bodySha256(request.body());
        ClientAccessSignatureInput signatureInput = new ClientAccessSignatureInput(
                ClientAccessV1.VERSION,
                ClientAccessDirection.CLIENT_TO_SERVER,
                "client-a",
                "request-001",
                ClientAccessOperation.LOGIN_AUTHORIZE,
                "idempotency-001",
                request.headers().get(ClientAccessHeaders.TIMESTAMP),
                "0123456789abcdef0123456789abcdef",
                bodyHash);
        assertThat(request.headers())
                .containsEntry(ClientAccessHeaders.BODY_SHA256, bodyHash)
                .containsEntry(ClientAccessHeaders.SIGNATURE, ClientAccessSigner.sign(signatureInput, SECRET));
    }

    @Test
    void retryKeepsIdempotencyKeyButUsesFreshRequestAndNonce() {
        RecordingTransport transport = new RecordingTransport(List.of(
                new ClientAccessHttpResponse(503, "{}".getBytes(StandardCharsets.UTF_8)),
                successAuthorization()));
        ClientAccessV1Client client = client(transport,
                sequence("request-001", "request-002"),
                sequence("0123456789abcdef0123456789abcdef", "fedcba9876543210fedcba9876543210"));

        client.authorize(new LoginAuthorizationRequest(
                "abcdef0123456789abcdef0123456789", "default", null),
                "idempotency-001");

        assertThat(transport.requests).hasSize(2);
        assertThat(transport.requests).extracting(request -> request.headers().get(ClientAccessHeaders.IDEMPOTENCY_KEY))
                .containsExactly("idempotency-001", "idempotency-001");
        assertThat(transport.requests).extracting(request -> request.headers().get(ClientAccessHeaders.REQUEST_ID))
                .containsExactly("request-001", "request-002");
        assertThat(transport.requests).extracting(request -> request.headers().get(ClientAccessHeaders.NONCE))
                .containsExactly("0123456789abcdef0123456789abcdef", "fedcba9876543210fedcba9876543210");
        assertThat(transport.requests).extracting(request -> request.headers().get(ClientAccessHeaders.SIGNATURE))
                .doesNotHaveDuplicates();
    }

    @Test
    void invalidResponsePreservesTheDecodingCause() {
        String invalid = """
                {"success":false,"code":"CAP-3001","message":"invalid redirect","requestId":"server-request",\
                "timestamp":"2026-07-13T03:00:00Z","data":"","details":[]}
                """;
        RecordingTransport transport = new RecordingTransport(List.of(
                new ClientAccessHttpResponse(400, invalid.getBytes(StandardCharsets.UTF_8))));
        ClientAccessV1Client client = client(transport, () -> "request-001",
                () -> "0123456789abcdef0123456789abcdef");

        assertThatThrownBy(() -> client.authorize(new LoginAuthorizationRequest(
                "abcdef0123456789abcdef0123456789", null, null),
                "idempotency-001"))
                .isInstanceOf(ClientAccessRemoteException.class)
                .hasMessage("Client Access response is invalid")
                .hasCauseInstanceOf(Exception.class);
    }

    private static ClientAccessV1Client client(RecordingTransport transport,
            Supplier<String> requestIds, Supplier<String> nonces) {
        ClientAccessRegistration registration = new ClientAccessRegistration(
                URI.create("https://sso.example.com/api/sso"), "client-a", SECRET);
        return new ClientAccessV1Client(new ObjectMapper().findAndRegisterModules(),
                () -> registration, transport, Clock.fixed(Instant.parse("2026-07-13T03:00:00Z"), ZoneOffset.UTC),
                requestIds, nonces, 2);
    }

    private static ClientAccessHttpResponse successAuthorization() {
        String json = """
                {"success":true,"code":"CAP-0000","message":"success","requestId":"server-request",\
                "timestamp":"2026-07-13T03:00:00Z","data":{"authorizationRequestId":"auth-001",\
                "authorizationUrl":"https://auth.example.com/login?request=auth-001",\
                "expiresAt":"2026-07-13T03:10:00Z"},"details":[]}
                """;
        return new ClientAccessHttpResponse(201, json.getBytes(StandardCharsets.UTF_8));
    }

    @SafeVarargs
    private static <T> Supplier<T> sequence(T... values) {
        List<T> remaining = new ArrayList<>(List.of(values));
        return () -> remaining.remove(0);
    }

    private static final class RecordingTransport implements ClientAccessHttpTransport {
        private final List<ClientAccessHttpResponse> responses;
        private final List<ClientAccessHttpRequest> requests = new ArrayList<>();
        private int index;

        private RecordingTransport(List<ClientAccessHttpResponse> responses) {
            this.responses = responses;
        }

        @Override
        public ClientAccessHttpResponse exchange(ClientAccessHttpRequest request) {
            requests.add(request);
            return responses.get(index++);
        }
    }
}
