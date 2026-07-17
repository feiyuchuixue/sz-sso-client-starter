package com.sz.ssoclient.clientaccess.web;

import com.sz.ssoclient.clientaccess.http.ClientAccessV1Client;
import com.sz.ssoclient.spi.SsoUserMappingService;
import com.sz.ssocore.clientaccess.v1.dto.PortalTicketCreateRequest;
import com.sz.ssocore.clientaccess.v1.dto.PortalTicketCreateResponse;
import com.sz.ssocore.clientaccess.v1.dto.SignoutCreateRequest;
import com.sz.ssocore.clientaccess.v1.dto.SignoutCreateResponse;
import com.sz.ssocore.clientaccess.v1.dto.SignoutScope;

import java.util.Objects;
import java.util.function.Supplier;

/** Portal and three-scope logout orchestration for browser-facing CAP endpoints. */
public class ClientAccessWebService {

    private static final String DEFAULT_PORTAL_TARGET = "/ucenter/applications";

    private final ClientAccessV1Client capClient;
    private final ClientLocalSessionAccessor sessions;
    private final SsoUserMappingService mappings;
    private final Supplier<String> idempotencyKeySupplier;

    public ClientAccessWebService(ClientAccessV1Client capClient,
            ClientLocalSessionAccessor sessions,
            SsoUserMappingService mappings,
            Supplier<String> idempotencyKeySupplier) {
        this.capClient = Objects.requireNonNull(capClient, "capClient");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.mappings = Objects.requireNonNull(mappings, "mappings");
        this.idempotencyKeySupplier = Objects.requireNonNull(idempotencyKeySupplier, "idempotencyKeySupplier");
    }

    public ClientLocalSession currentSession() {
        return sessions.current();
    }

    public void logoutLocalSession() {
        sessions.logoutCurrentSession();
    }

    public PortalTicketCreateResponse createPortalEntry(String targetPath) {
        ClientLocalSession session = requiredSession();
        String centerUserId = centerUserId(session.localUserId());
        String safeTarget = targetPath == null || targetPath.isBlank() ? DEFAULT_PORTAL_TARGET : targetPath;
        return requireData(capClient.createPortalTicket(
                new PortalTicketCreateRequest(centerUserId, safeTarget), idempotency()).data());
    }

    public SignoutCreateResponse signoutCurrentDevice() {
        ClientLocalSession session = requiredSession();
        SignoutCreateResponse response = requireData(capClient.createSignout(
                new SignoutCreateRequest(centerUserId(session.localUserId()), SignoutScope.CURRENT_DEVICE,
                        session.deviceId(), "CLIENT_DEVICE_SIGNOUT"), idempotency()).data());
        sessions.logoutCurrentDevice(session.localUserId(), session.deviceId());
        return response;
    }

    public SignoutCreateResponse signoutAccount() {
        ClientLocalSession session = requiredSession();
        SignoutCreateResponse response = requireData(capClient.createSignout(
                new SignoutCreateRequest(centerUserId(session.localUserId()), SignoutScope.ACCOUNT_GLOBAL,
                        null, "CLIENT_ACCOUNT_SIGNOUT"), idempotency()).data());
        sessions.logoutAccount(session.localUserId());
        return response;
    }

    private ClientLocalSession requiredSession() {
        ClientLocalSession session = sessions.current();
        if (session == null || !session.authenticated() || session.localUserId() == null) {
            throw new ClientAccessWebException("CAP-4001", 401, "Client local session is required");
        }
        return session;
    }

    private String centerUserId(Object localUserId) {
        Object centerUserId = mappings.toServerUserId(localUserId);
        if (centerUserId == null) {
            throw new ClientAccessWebException("CAP-4002", 403, "Client user relation is required");
        }
        return centerUserId.toString();
    }

    private String idempotency() {
        String value = idempotencyKeySupplier.get();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("idempotency key supplier returned an empty value");
        }
        return value;
    }

    private static <T> T requireData(T data) {
        if (data == null) {
            throw new ClientAccessWebException("CAP-5002", 500, "SSO Server returned no operation result");
        }
        return data;
    }
}
