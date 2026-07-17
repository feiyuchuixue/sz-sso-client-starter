package com.sz.ssoclient.clientaccess.web;

import com.sz.ssoclient.clientaccess.http.ClientAccessRegistration;
import com.sz.ssoclient.clientaccess.http.ClientAccessRegistrationProvider;
import com.sz.ssoclient.clientaccess.http.ClientAccessRemoteException;
import com.sz.ssoclient.clientaccess.http.ClientAccessV1Client;
import com.sz.ssoclient.login.SsoLoginCommand;
import com.sz.ssoclient.pojo.SsoLoginResult;
import com.sz.ssoclient.service.SsoClientService;
import com.sz.ssoclient.spi.SsoUserMappingService;
import com.sz.ssocore.clientaccess.v1.ClientAccessSigner;
import com.sz.ssocore.clientaccess.v1.dto.LoginAuthorizationRequest;
import com.sz.ssocore.clientaccess.v1.dto.LoginAuthorizationResponse;
import com.sz.ssocore.clientaccess.v1.dto.LoginTicketExchangeRequest;
import com.sz.ssocore.clientaccess.v1.dto.LoginTicketExchangeResponse;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

/** Secure browser login transaction and CAP ticket exchange orchestration. */
public class ClientLoginTransactionService {

    private final ClientAccessV1Client capClient;
    private final ClientAccessRegistrationProvider registrationProvider;
    private final SsoUserMappingService mappingService;
    private final SsoClientService loginService;
    private final ClientLoginTransactionStore store;
    private final Clock clock;
    private final Supplier<String> randomTokenSupplier;
    private final int transactionTtlSeconds;
    private final int maxPendingPerBrowser;
    private final long localSessionTimeoutSeconds;

    public ClientLoginTransactionService(ClientAccessV1Client capClient,
            ClientAccessRegistrationProvider registrationProvider,
            SsoUserMappingService mappingService,
            SsoClientService loginService,
            ClientLoginTransactionStore store,
            Clock clock,
            Supplier<String> randomTokenSupplier,
            int transactionTtlSeconds,
            int maxPendingPerBrowser,
            long localSessionTimeoutSeconds) {
        this.capClient = Objects.requireNonNull(capClient, "capClient");
        this.registrationProvider = Objects.requireNonNull(registrationProvider, "registrationProvider");
        this.mappingService = Objects.requireNonNull(mappingService, "mappingService");
        this.loginService = Objects.requireNonNull(loginService, "loginService");
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.randomTokenSupplier = Objects.requireNonNull(randomTokenSupplier, "randomTokenSupplier");
        if (transactionTtlSeconds <= 0 || maxPendingPerBrowser <= 0 || localSessionTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("Login transaction limits must be positive");
        }
        this.transactionTtlSeconds = transactionTtlSeconds;
        this.maxPendingPerBrowser = maxPendingPerBrowser;
        this.localSessionTimeoutSeconds = localSessionTimeoutSeconds;
    }

    public ClientLoginTransactionCreated create(String browserSessionId, String back, String mode, String theme) {
        String safeBrowserSessionId = required(browserSessionId, "Browser session is required");
        String safeBack = safeBack(back);
        ClientAccessRegistration registration = registrationProvider.current();
        String state = secureToken("state");
        String authorizeIdempotencyKey = secureToken("authorization idempotency key");
        String exchangeIdempotencyKey = secureToken("exchange idempotency key");
        Instant now = clock.instant();
        Instant localExpiry = now.plusSeconds(transactionTtlSeconds);
        String stateHash = hash(state);
        String browserHash = hash(safeBrowserSessionId);
        ClientLoginTransaction transaction = new ClientLoginTransaction(
                stateHash, browserHash, registration.clientFlag(),
                safeBack, null, exchangeIdempotencyKey,
                now, localExpiry, ClientLoginTransactionStatus.AUTHORIZING, null);
        store.create(transaction, maxPendingPerBrowser);
        try {
            LoginAuthorizationResponse authorization = requireData(capClient.authorize(
                    new LoginAuthorizationRequest(state, mode, theme),
                    authorizeIdempotencyKey).data(), "SSO Server returned no authorization data");
            store.markAuthorized(stateHash, browserHash, authorization.authorizationRequestId(),
                    authorization.expiresAt());
            ClientLoginTransaction authorized = requireData(store.find(stateHash),
                    "Authorized login transaction is missing");
            return new ClientLoginTransactionCreated(authorization.authorizationUrl(), authorized.expiresAt());
        } catch (RuntimeException exception) {
            store.delete(stateHash);
            throw exception;
        }
    }

    public ClientLoginCallbackResult complete(String browserSessionId, String state, String ticket) {
        String stateHash = hash(required(state, "Login state is required"));
        String browserHash = hash(required(browserSessionId, "Browser session is required"));
        String safeTicket = required(ticket, "Login Ticket is required");
        ClientLoginTransaction transaction = store.beginExchange(stateHash, browserHash, clock.instant());
        if (transaction.status() == ClientLoginTransactionStatus.COMPLETED) {
            return new ClientLoginCallbackResult(transaction.completedResult(), transaction.back());
        }
        try {
            LoginTicketExchangeResponse exchange = requireData(capClient.exchangeTicket(
                    new LoginTicketExchangeRequest(transaction.authorizationRequestId(), safeTicket, state),
                    transaction.exchangeIdempotencyKey()).data(),
                    "SSO Server returned no Ticket exchange data");
            if (exchange.ssoUser() == null || exchange.authentication() == null || exchange.clientAuthorities() == null) {
                throw invalid("Ticket exchange response is incomplete");
            }
            Object mapped = mappingService.resolveOrProvisionClientUser(exchange.ssoUser().id());
            Long localUserId = localUserId(mapped);
            SsoLoginResult loginResult = requireData(loginService.login(new SsoLoginCommand(
                    exchange.ssoUser().id(), localUserId, exchange.authentication().deviceId(),
                    localSessionTimeoutSeconds, exchange.clientAuthorities().isSuperAdmin())),
                    "Client login adapter returned no result");
            store.complete(stateHash, browserHash, loginResult);
            return new ClientLoginCallbackResult(loginResult, transaction.back());
        } catch (RuntimeException exception) {
            if (isTemporary(exception)) {
                store.resetToCreated(stateHash, browserHash);
            } else {
                store.delete(stateHash);
            }
            throw exception;
        }
    }

    private static String safeBack(String candidate) {
        String value = candidate == null || candidate.isBlank() ? "/" : candidate.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (!value.startsWith("/") || value.startsWith("//") || value.indexOf('\\') >= 0
                || lower.contains("://") || lower.startsWith("/login") || lower.startsWith("/sso-login")) {
            throw new ClientLoginTransactionException("CAP-1001", "back must be a safe same-site relative path");
        }
        return value;
    }

    private String secureToken(String field) {
        String token = required(randomTokenSupplier.get(), field + " is required");
        if (token.length() < 22) {
            throw new IllegalStateException(field + " must contain at least 128 bits of random entropy");
        }
        return token;
    }

    private static Long localUserId(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.valueOf(value.toString());
            } catch (NumberFormatException ignored) {
                // handled below
            }
        }
        throw new ClientLoginTransactionException("CAP-4002", "Client user relation is required");
    }

    private static boolean isTemporary(RuntimeException exception) {
        if (!(exception instanceof ClientAccessRemoteException remote)) {
            return false;
        }
        return remote.getHttpStatus() >= 500
                || remote.getResponse() != null && "CAP-5001".equals(remote.getResponse().code());
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw invalid(message);
        }
        return value;
    }

    private static <T> T requireData(T value, String message) {
        if (value == null) {
            throw invalid(message);
        }
        return value;
    }

    private static String hash(String value) {
        return ClientAccessSigner.bodySha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static ClientLoginTransactionException invalid(String message) {
        return new ClientLoginTransactionException("CAP-3002", message);
    }
}
