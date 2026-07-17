package com.sz.ssoclient.clientaccess.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sz.ssocore.clientaccess.v1.ClientAccessHeaders;
import com.sz.ssocore.clientaccess.v1.ClientAccessOperation;
import com.sz.ssocore.clientaccess.v1.dto.ClientRoleQueryRequest;
import com.sz.ssocore.clientaccess.v1.dto.ClientSuperAdminSetRequest;
import com.sz.ssocore.clientaccess.v1.dto.LoginTicketExchangeRequest;
import com.sz.ssocore.clientaccess.v1.dto.PortalTicketCreateRequest;
import com.sz.ssocore.clientaccess.v1.dto.SignoutCreateRequest;
import com.sz.ssocore.clientaccess.v1.dto.SignoutScope;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAccessV1OperationsTest {

    @Test
    void exposesEveryClientToServerOperationAndUnsignedCapabilities() {
        RecordingSuccessTransport transport = new RecordingSuccessTransport();
        ClientAccessRegistration registration = new ClientAccessRegistration(
                URI.create("https://sso.example.com/api/sso"), "client-a",
                "secret".getBytes(StandardCharsets.UTF_8));
        ClientAccessV1Client client = new ClientAccessV1Client(new ObjectMapper().findAndRegisterModules(),
                () -> registration, transport, Clock.fixed(Instant.parse("2026-07-13T03:00:00Z"), ZoneOffset.UTC),
                () -> UUID.randomUUID().toString(), () -> UUID.randomUUID().toString().replace("-", ""), 1);

        client.capabilities();
        client.exchangeTicket(new LoginTicketExchangeRequest("auth-1", "ticket-1", "a".repeat(32)), "idem-exchange");
        client.createPortalTicket(new PortalTicketCreateRequest("10001", "/ucenter/profile"), "idem-portal");
        client.queryRoles(new ClientRoleQueryRequest("10001"), "idem-role");
        client.setSuperAdmin(new ClientSuperAdminSetRequest("10001", true, 7L), "idem-admin");
        client.createSignout(new SignoutCreateRequest("10001", SignoutScope.CURRENT_DEVICE,
                "device-1", "USER_INITIATED"), "idem-signout");

        assertThat(transport.requests).hasSize(6);
        assertThat(transport.requests.get(0).method()).isEqualTo("GET");
        assertThat(transport.requests.get(0).uri().getPath()).endsWith("/client-api/v1/capabilities");
        assertThat(transport.requests.get(0).headers()).doesNotContainKeys(
                ClientAccessHeaders.SIGNATURE, ClientAccessHeaders.IDEMPOTENCY_KEY);
        assertThat(transport.requests.subList(1, 6))
                .extracting(request -> request.headers().get(ClientAccessHeaders.OPERATION))
                .containsExactly(
                        ClientAccessOperation.LOGIN_TICKET_EXCHANGE.name(),
                        ClientAccessOperation.PORTAL_TICKET_CREATE.name(),
                        ClientAccessOperation.CLIENT_ROLE_QUERY.name(),
                        ClientAccessOperation.CLIENT_SUPER_ADMIN_SET.name(),
                        ClientAccessOperation.SIGNOUT_CREATE.name());
        assertThat(transport.requests.subList(1, 6)).extracting(ClientAccessHttpRequest::method)
                .containsExactly("POST", "POST", "POST", "PUT", "POST");
    }

    private static final class RecordingSuccessTransport implements ClientAccessHttpTransport {
        private final List<ClientAccessHttpRequest> requests = new ArrayList<>();

        @Override
        public ClientAccessHttpResponse exchange(ClientAccessHttpRequest request) {
            requests.add(request);
            String data = request.uri().getPath().endsWith("/capabilities")
                    ? "{\"product\":\"sz-sso-client-access\",\"protocolVersions\":[\"1.0\"],"
                            + "\"signatureAlgorithms\":[\"HMAC-SHA256\"],\"bodyHashAlgorithms\":[\"SHA-256\"],"
                            + "\"maxClockSkewSeconds\":300,\"nonceTtlSeconds\":600,"
                            + "\"loginAuthorizationTtlSeconds\":600,\"loginTicketTtlSeconds\":60,"
                            + "\"portalTicketTtlSeconds\":60,\"operations\":[],\"messageTypes\":[]}"
                    : "{}";
            String body = "{\"success\":true,\"code\":\"CAP-0000\",\"message\":\"success\","
                    + "\"requestId\":\"server-request\",\"timestamp\":\"2026-07-13T03:00:00Z\","
                    + "\"data\":" + data + ",\"details\":[]}";
            int status = request.method().equals("GET") ? 200 : 200;
            return new ClientAccessHttpResponse(status, body.getBytes(StandardCharsets.UTF_8));
        }
    }
}
