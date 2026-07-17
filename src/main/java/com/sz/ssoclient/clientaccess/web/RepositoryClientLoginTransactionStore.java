package com.sz.ssoclient.clientaccess.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sz.ssoclient.clientaccess.json.ClientAccessJsonCodec;
import com.sz.ssoclient.clientaccess.state.ClientAccessStateKeys;
import com.sz.ssoclient.clientaccess.state.ClientAccessStateRepository;
import com.sz.ssoclient.pojo.SsoLoginResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

/** Atomic repository-backed login transaction store for clustered Clients. */
public final class RepositoryClientLoginTransactionStore implements ClientLoginTransactionStore {

    private static final int MAX_CAS_ATTEMPTS = 16;

    private final ClientAccessStateRepository repository;
    private final ClientAccessJsonCodec json;
    private final Clock clock;
    private final Supplier<String> clientFlagSupplier;

    public RepositoryClientLoginTransactionStore(ClientAccessStateRepository repository,
            ClientAccessJsonCodec json, Clock clock, String clientFlag) {
        this(repository, json, clock, () -> clientFlag);
    }

    public RepositoryClientLoginTransactionStore(ClientAccessStateRepository repository,
            ClientAccessJsonCodec json, Clock clock, Supplier<String> clientFlagSupplier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.json = Objects.requireNonNull(json, "json");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.clientFlagSupplier = Objects.requireNonNull(clientFlagSupplier, "clientFlagSupplier");
    }

    @Override
    public void create(ClientLoginTransaction transaction, int maxPendingPerBrowser) {
        Objects.requireNonNull(transaction, "transaction");
        if (maxPendingPerBrowser <= 0) {
            throw new IllegalArgumentException("maxPendingPerBrowser must be positive");
        }
        Duration ttl = ttl(transaction.expiresAt());
        int slot = claimSlot(transaction.browserSessionHash(), transaction.stateHash(), maxPendingPerBrowser, ttl);
        StoredTransaction stored = new StoredTransaction(transaction, slot);
        try {
            if (!repository.putIfAbsent(transactionKey(transaction.stateHash()), encode(stored), ttl)) {
                releaseSlot(stored);
                throw invalid("Login state collision");
            }
        } catch (RuntimeException exception) {
            releaseSlot(stored);
            throw exception;
        }
    }

    @Override
    public ClientLoginTransaction find(String stateHash) {
        StoredValue stored = read(stateHash);
        if (stored == null) {
            return null;
        }
        if (!stored.value().transaction().expiresAt().isAfter(clock.instant())) {
            delete(stateHash);
            return null;
        }
        return stored.value().transaction();
    }

    @Override
    public ClientLoginTransaction beginExchange(String stateHash, String browserSessionHash, Instant now) {
        for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
            StoredValue stored = requireStored(stateHash, "Login transaction is missing or expired");
            ClientLoginTransaction current = stored.value().transaction();
            if (!current.expiresAt().isAfter(now)) {
                delete(stateHash);
                throw invalid("Login transaction is missing or expired");
            }
            requireBrowser(current, browserSessionHash);
            if (current.status() == ClientLoginTransactionStatus.COMPLETED) {
                return current;
            }
            if (current.status() == ClientLoginTransactionStatus.EXCHANGING) {
                throw invalid("Login transaction is already being exchanged");
            }
            if (current.status() != ClientLoginTransactionStatus.CREATED) {
                throw invalid("Login transaction is not ready for exchange");
            }
            ClientLoginTransaction exchanging = current.withStatus(ClientLoginTransactionStatus.EXCHANGING);
            if (replace(stateHash, stored, new StoredTransaction(exchanging, stored.value().slot()))) {
                return exchanging;
            }
        }
        throw invalid("Login transaction changed concurrently");
    }

    @Override
    public void markAuthorized(String stateHash, String browserSessionHash, String authorizationRequestId,
            Instant expiresAt) {
        mutate(stateHash, browserSessionHash, current -> {
            if (current.status() != ClientLoginTransactionStatus.AUTHORIZING) {
                throw invalid("Login transaction cannot be authorized");
            }
            return current.authorized(authorizationRequestId, expiresAt);
        }, "Login transaction cannot be authorized");
    }

    @Override
    public void complete(String stateHash, String browserSessionHash, SsoLoginResult result) {
        StoredTransaction completed = mutate(stateHash, browserSessionHash, current -> {
            if (current.status() == ClientLoginTransactionStatus.COMPLETED) {
                return current;
            }
            if (current.status() != ClientLoginTransactionStatus.EXCHANGING) {
                throw invalid("Login transaction cannot be completed");
            }
            return current.completed(result);
        }, "Login transaction cannot be completed");
        releaseSlot(completed);
    }

    @Override
    public void resetToCreated(String stateHash, String browserSessionHash) {
        for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
            StoredValue stored = read(stateHash);
            if (stored == null) {
                return;
            }
            ClientLoginTransaction current = stored.value().transaction();
            if (!same(current.browserSessionHash(), browserSessionHash)
                    || current.status() != ClientLoginTransactionStatus.EXCHANGING) {
                return;
            }
            ClientLoginTransaction reset = current.withStatus(ClientLoginTransactionStatus.CREATED);
            if (replace(stateHash, stored, new StoredTransaction(reset, stored.value().slot()))) {
                return;
            }
        }
        throw invalid("Login transaction changed concurrently");
    }

    @Override
    public void delete(String stateHash) {
        for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
            StoredValue stored = read(stateHash);
            if (stored == null) {
                return;
            }
            if (repository.compareAndDelete(transactionKey(stateHash), stored.raw())) {
                releaseSlot(stored.value());
                return;
            }
        }
        throw invalid("Login transaction changed concurrently");
    }

    private StoredTransaction mutate(String stateHash, String browserSessionHash,
            java.util.function.UnaryOperator<ClientLoginTransaction> mutation, String missingMessage) {
        for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
            StoredValue stored = requireStored(stateHash, missingMessage);
            ClientLoginTransaction current = stored.value().transaction();
            requireBrowser(current, browserSessionHash);
            StoredTransaction updated = new StoredTransaction(mutation.apply(current), stored.value().slot());
            if (replace(stateHash, stored, updated)) {
                return updated;
            }
        }
        throw invalid("Login transaction changed concurrently");
    }

    private boolean replace(String stateHash, StoredValue current, StoredTransaction updated) {
        return repository.compareAndSet(transactionKey(stateHash), current.raw(), encode(updated),
                ttl(updated.transaction().expiresAt()));
    }

    private int claimSlot(String browserHash, String stateHash, int maximum, Duration ttl) {
        for (int slot = 0; slot < maximum; slot++) {
            if (repository.putIfAbsent(browserSlotKey(browserHash, slot), stateHash, ttl)) {
                return slot;
            }
        }
        throw invalid("Too many pending login transactions for this browser session");
    }

    private void releaseSlot(StoredTransaction stored) {
        repository.compareAndDelete(browserSlotKey(stored.transaction().browserSessionHash(), stored.slot()),
                stored.transaction().stateHash());
    }

    private StoredValue requireStored(String stateHash, String message) {
        StoredValue value = read(stateHash);
        if (value == null) {
            throw invalid(message);
        }
        return value;
    }

    private StoredValue read(String stateHash) {
        String raw = repository.get(transactionKey(stateHash));
        return raw == null ? null : new StoredValue(raw, decode(raw));
    }

    private String transactionKey(String stateHash) {
        return prefix() + "login:transaction:" + ClientAccessStateKeys.digest(stateHash);
    }

    private String browserSlotKey(String browserHash, int slot) {
        return prefix() + "login:browser:" + ClientAccessStateKeys.digest(browserHash) + ':' + slot;
    }

    private String prefix() {
        return ClientAccessStateKeys.namespace(clientFlagSupplier.get());
    }

    private Duration ttl(Instant expiresAt) {
        Duration ttl = Duration.between(clock.instant(), Objects.requireNonNull(expiresAt, "expiresAt"));
        if (ttl.isZero() || ttl.isNegative()) {
            throw invalid("Login transaction is missing or expired");
        }
        return ttl;
    }

    private String encode(StoredTransaction value) {
        try {
            return new String(json.writeValueAsBytes(value), StandardCharsets.UTF_8);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to encode CAP login transaction", exception);
        }
    }

    private StoredTransaction decode(String value) {
        try {
            return json.readValue(value.getBytes(StandardCharsets.UTF_8), StoredTransaction.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to decode CAP login transaction", exception);
        }
    }

    private static void requireBrowser(ClientLoginTransaction current, String browserHash) {
        if (!same(current.browserSessionHash(), browserHash)) {
            throw invalid("Login transaction does not belong to this browser session");
        }
    }

    private static boolean same(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
    }

    private static ClientLoginTransactionException invalid(String message) {
        return new ClientLoginTransactionException("CAP-3002", message);
    }

    private record StoredValue(String raw, StoredTransaction value) {
    }

    private record StoredTransaction(ClientLoginTransaction transaction, int slot) {
        private StoredTransaction {
            Objects.requireNonNull(transaction, "transaction");
            if (slot < 0) {
                throw new IllegalArgumentException("slot must not be negative");
            }
        }
    }
}
