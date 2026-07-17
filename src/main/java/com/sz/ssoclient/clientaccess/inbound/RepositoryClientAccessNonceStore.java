package com.sz.ssoclient.clientaccess.inbound;

import com.sz.ssoclient.clientaccess.state.ClientAccessStateKeys;
import com.sz.ssoclient.clientaccess.state.ClientAccessStateRepository;
import com.sz.ssocore.clientaccess.v1.ClientAccessNonceKey;
import com.sz.ssocore.clientaccess.v1.ClientAccessNonceStore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Repository-backed nonce claim shared by all Client instances. */
public final class RepositoryClientAccessNonceStore implements ClientAccessNonceStore {

    private final ClientAccessStateRepository repository;
    private final Clock clock;

    public RepositoryClientAccessNonceStore(ClientAccessStateRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean tryClaim(ClientAccessNonceKey key, Instant expiresAt) {
        Objects.requireNonNull(key, "key");
        String canonical = key.protocolVersion() + '|' + key.direction() + '|' + key.clientFlag() + '|' + key.nonce();
        String stateKey = ClientAccessStateKeys.namespace(key.clientFlag())
                + "nonce:" + ClientAccessStateKeys.digest(canonical);
        return repository.putIfAbsent(stateKey, "1", ttl(expiresAt));
    }

    private Duration ttl(Instant expiresAt) {
        Duration ttl = Duration.between(clock.instant(), Objects.requireNonNull(expiresAt, "expiresAt"));
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("nonce expiry must be in the future");
        }
        return ttl;
    }
}
