package com.sz.ssoclient.clientaccess.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sz.ssoclient.clientaccess.json.ClientAccessJsonCodec;
import com.sz.ssocore.clientaccess.v1.ClientAccessDirection;
import com.sz.ssocore.clientaccess.v1.ClientAccessEndpointResolver;
import com.sz.ssocore.clientaccess.v1.ClientAccessHeaders;
import com.sz.ssocore.clientaccess.v1.ClientAccessOperation;
import com.sz.ssocore.clientaccess.v1.ClientAccessSignatureInput;
import com.sz.ssocore.clientaccess.v1.ClientAccessSigner;
import com.sz.ssocore.clientaccess.v1.ClientAccessV1;
import com.sz.ssocore.clientaccess.v1.dto.CapabilitiesData;
import com.sz.ssocore.clientaccess.v1.dto.ClientAccessResponse;
import com.sz.ssocore.clientaccess.v1.dto.ClientRoleQueryRequest;
import com.sz.ssocore.clientaccess.v1.dto.ClientRoleQueryResponse;
import com.sz.ssocore.clientaccess.v1.dto.ClientSuperAdminSetRequest;
import com.sz.ssocore.clientaccess.v1.dto.ClientSuperAdminSetResponse;
import com.sz.ssocore.clientaccess.v1.dto.LoginAuthorizationRequest;
import com.sz.ssocore.clientaccess.v1.dto.LoginAuthorizationResponse;
import com.sz.ssocore.clientaccess.v1.dto.LoginTicketExchangeRequest;
import com.sz.ssocore.clientaccess.v1.dto.LoginTicketExchangeResponse;
import com.sz.ssocore.clientaccess.v1.dto.PortalTicketCreateRequest;
import com.sz.ssocore.clientaccess.v1.dto.PortalTicketCreateResponse;
import com.sz.ssocore.clientaccess.v1.dto.SignoutCreateRequest;
import com.sz.ssocore.clientaccess.v1.dto.SignoutCreateResponse;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** CAP V1 signed HTTP client for confidential Java backends. */
public class ClientAccessV1Client {

    private final ClientAccessJsonCodec jsonCodec;
    private final ClientAccessRegistrationProvider registrationProvider;
    private final ClientAccessHttpTransport transport;
    private final Clock clock;
    private final Supplier<String> requestIdSupplier;
    private final Supplier<String> nonceSupplier;
    private final int maxAttempts;

    public ClientAccessV1Client(ObjectMapper objectMapper,
            ClientAccessRegistrationProvider registrationProvider,
            ClientAccessHttpTransport transport,
            Clock clock,
            Supplier<String> requestIdSupplier,
            Supplier<String> nonceSupplier,
            int maxAttempts) {
        this(new ClientAccessJsonCodec(objectMapper), registrationProvider, transport, clock,
                requestIdSupplier, nonceSupplier, maxAttempts);
    }

    public ClientAccessV1Client(ClientAccessJsonCodec jsonCodec,
            ClientAccessRegistrationProvider registrationProvider,
            ClientAccessHttpTransport transport,
            Clock clock,
            Supplier<String> requestIdSupplier,
            Supplier<String> nonceSupplier,
            int maxAttempts) {
        this.jsonCodec = Objects.requireNonNull(jsonCodec, "jsonCodec");
        this.registrationProvider = Objects.requireNonNull(registrationProvider, "registrationProvider");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.requestIdSupplier = Objects.requireNonNull(requestIdSupplier, "requestIdSupplier");
        this.nonceSupplier = Objects.requireNonNull(nonceSupplier, "nonceSupplier");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        this.maxAttempts = maxAttempts;
    }

    public ClientAccessResponse<LoginAuthorizationResponse> authorize(
            LoginAuthorizationRequest request, String idempotencyKey) {
        return execute(ClientAccessOperation.LOGIN_AUTHORIZE, request, LoginAuthorizationResponse.class, idempotencyKey);
    }

    public ClientAccessResponse<CapabilitiesData> capabilities() {
        ClientAccessRegistration registration = registrationProvider.current();
        ClientAccessHttpRequest request = new ClientAccessHttpRequest("GET",
                ClientAccessEndpointResolver.capabilitiesEndpoint(registration.serverBaseUri().toASCIIString()),
                Map.of("Accept", "application/json"), new byte[0]);
        ClientAccessHttpResponse response = transport.exchange(request);
        ClientAccessResponse<CapabilitiesData> parsed = deserialize(response, CapabilitiesData.class);
        if (response.statusCode() < 200 || response.statusCode() >= 300 || !parsed.success()) {
            throw new ClientAccessRemoteException(response.statusCode(), parsed);
        }
        if (parsed.data() == null || !parsed.data().protocolVersions().contains(ClientAccessV1.VERSION)) {
            throw new ClientAccessRemoteException(400, new ClientAccessResponse<>(false, "CAP-1002",
                    "Server does not advertise Client Access Protocol V1", parsed.requestId(),
                    clock.instant(), null, List.of()));
        }
        return parsed;
    }

    public ClientAccessResponse<LoginTicketExchangeResponse> exchangeTicket(
            LoginTicketExchangeRequest request, String idempotencyKey) {
        return execute(ClientAccessOperation.LOGIN_TICKET_EXCHANGE, request,
                LoginTicketExchangeResponse.class, idempotencyKey);
    }

    public ClientAccessResponse<PortalTicketCreateResponse> createPortalTicket(
            PortalTicketCreateRequest request, String idempotencyKey) {
        return execute(ClientAccessOperation.PORTAL_TICKET_CREATE, request,
                PortalTicketCreateResponse.class, idempotencyKey);
    }

    public ClientAccessResponse<ClientRoleQueryResponse> queryRoles(
            ClientRoleQueryRequest request, String idempotencyKey) {
        return execute(ClientAccessOperation.CLIENT_ROLE_QUERY, request,
                ClientRoleQueryResponse.class, idempotencyKey);
    }

    public ClientAccessResponse<ClientSuperAdminSetResponse> setSuperAdmin(
            ClientSuperAdminSetRequest request, String idempotencyKey) {
        return execute(ClientAccessOperation.CLIENT_SUPER_ADMIN_SET, request,
                ClientSuperAdminSetResponse.class, idempotencyKey);
    }

    public ClientAccessResponse<SignoutCreateResponse> createSignout(
            SignoutCreateRequest request, String idempotencyKey) {
        return execute(ClientAccessOperation.SIGNOUT_CREATE, request,
                SignoutCreateResponse.class, idempotencyKey);
    }
    private <T> ClientAccessResponse<T> execute(ClientAccessOperation operation, Object request,
            Class<T> responseType, String idempotencyKey) {
        byte[] body = serialize(request);
        ClientAccessRegistration registration = registrationProvider.current();
        ClientAccessHttpResponse response = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            ClientAccessHttpRequest httpRequest = signedRequest(operation, registration, body, idempotencyKey);
            try {
                response = transport.exchange(httpRequest);
            } catch (ClientAccessTransportException exception) {
                if (attempt == maxAttempts) {
                    throw exception;
                }
                continue;
            }
            if (!retryable(response.statusCode()) || attempt == maxAttempts) {
                break;
            }
        }
        ClientAccessResponse<T> parsed = deserialize(Objects.requireNonNull(response), responseType);
        if (response.statusCode() < 200 || response.statusCode() >= 300 || !parsed.success()) {
            throw new ClientAccessRemoteException(response.statusCode(), parsed);
        }
        return parsed;
    }

    private ClientAccessHttpRequest signedRequest(ClientAccessOperation operation,
            ClientAccessRegistration registration, byte[] body, String idempotencyKey) {
        String requestId = requireGenerated(requestIdSupplier.get(), "requestId");
        String nonce = requireGenerated(nonceSupplier.get(), "nonce");
        String timestamp = clock.instant().toString();
        String bodyHash = ClientAccessSigner.bodySha256(body);
        ClientAccessSignatureInput input = new ClientAccessSignatureInput(
                ClientAccessV1.VERSION, ClientAccessDirection.CLIENT_TO_SERVER,
                registration.clientFlag(), requestId, operation,
                requireGenerated(idempotencyKey, "idempotencyKey"), timestamp, nonce, bodyHash);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json; charset=utf-8");
        headers.put(ClientAccessHeaders.PROTOCOL_VERSION, input.protocolVersion());
        headers.put(ClientAccessHeaders.DIRECTION, input.direction().name());
        headers.put(ClientAccessHeaders.CLIENT, input.clientFlag());
        headers.put(ClientAccessHeaders.REQUEST_ID, input.requestId());
        headers.put(ClientAccessHeaders.OPERATION, input.operation().name());
        headers.put(ClientAccessHeaders.IDEMPOTENCY_KEY, input.idempotencyKey());
        headers.put(ClientAccessHeaders.TIMESTAMP, input.timestamp());
        headers.put(ClientAccessHeaders.NONCE, input.nonce());
        headers.put(ClientAccessHeaders.BODY_SHA256, input.bodySha256());
        headers.put(ClientAccessHeaders.SIGNATURE, ClientAccessSigner.sign(input, registration.secret()));
        return new ClientAccessHttpRequest(operation.httpMethod(),
                ClientAccessEndpointResolver.capServerEndpoint(
                        registration.serverBaseUri().toASCIIString(), operation), headers, body);
    }

    private byte[] serialize(Object request) {
        try {
            return jsonCodec.writeValueAsBytes(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Client Access request cannot be serialized", exception);
        }
    }

    private <T> ClientAccessResponse<T> deserialize(ClientAccessHttpResponse response, Class<T> responseType) {
        try {
            return jsonCodec.readValue(response.body(), ClientAccessResponse.class, responseType);
        } catch (Exception exception) {
            throw new ClientAccessRemoteException(response.statusCode(), new ClientAccessResponse<>(
                    false, "CAP-5002", "Client Access response is invalid", "unavailable",
                    clock.instant(), null, List.of()), exception);
        }
    }

    private static boolean retryable(int statusCode) {
        return statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    private static String requireGenerated(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
