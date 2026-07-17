package com.sz.ssoclient.clientaccess.inbound;

import com.sz.ssoclient.clientaccess.state.ClientAccessStateKeys;
import com.sz.ssoclient.clientaccess.state.ClientAccessStateRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

/** Repository-backed idempotency state shared by all Client instances. */
public final class RepositoryClientInboundEventStore implements ClientInboundEventStore {

    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String COMPLETED = "COMPLETED";

    private final ClientAccessStateRepository repository;
    private final Supplier<String> clientFlagSupplier;
    private final Clock clock;

    public RepositoryClientInboundEventStore(ClientAccessStateRepository repository, String clientFlag, Clock clock) {
        this(repository, () -> clientFlag, clock);
    }

    public RepositoryClientInboundEventStore(ClientAccessStateRepository repository,
            Supplier<String> clientFlagSupplier, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clientFlagSupplier = Objects.requireNonNull(clientFlagSupplier, "clientFlagSupplier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ClientInboundEventStatus begin(String namespace, String eventId, Instant expiresAt) {
        String key = key(namespace, eventId);
        Duration ttl = ttl(expiresAt);
        for (int attempt = 0; attempt < 4; attempt++) {
            if (repository.putIfAbsent(key, IN_PROGRESS, ttl)) {
                return ClientInboundEventStatus.ACQUIRED;
            }
            String current = repository.get(key);
            if (COMPLETED.equals(current)) {
                return ClientInboundEventStatus.COMPLETED;
            }
            if (IN_PROGRESS.equals(current)) {
                return ClientInboundEventStatus.IN_PROGRESS;
            }
        }
        throw new IllegalStateException("CAP inbound event state changed repeatedly during claim");
    }

    @Override
    public void complete(String namespace, String eventId, Instant expiresAt) {
        String key = key(namespace, eventId);
        String current = repository.get(key);
        if (COMPLETED.equals(current)) {
            return;
        }
        if (!repository.compareAndSet(key, IN_PROGRESS, COMPLETED, ttl(expiresAt))) {
            throw new IllegalStateException("CAP inbound event lease is missing or changed");
        }
    }

    @Override
    public void release(String namespace, String eventId) {
        repository.compareAndDelete(key(namespace, eventId), IN_PROGRESS);
    }

    private String key(String namespace, String eventId) {
        String clientFlag = clientFlagSupplier.get();
        String logical = required(namespace, "event namespace") + '|' + required(eventId, "event id");
        return ClientAccessStateKeys.namespace(clientFlag) + "event:" + ClientAccessStateKeys.digest(logical);
    }

    private Duration ttl(Instant expiresAt) {
        Duration ttl = Duration.between(clock.instant(), Objects.requireNonNull(expiresAt, "expiresAt"));
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("event expiry must be in the future");
        }
        return ttl;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
