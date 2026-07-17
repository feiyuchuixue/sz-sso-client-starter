package com.sz.ssoclient.clientaccess.inbound;

import com.sz.ssocore.clientaccess.v1.ClientAccessNonceKey;
import com.sz.ssocore.clientaccess.v1.ClientAccessNonceStore;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Single-process nonce store; clusters must replace it with a shared atomic store. */
public class InMemoryClientAccessNonceStore implements ClientAccessNonceStore {

    private final ConcurrentHashMap<ClientAccessNonceKey, Instant> nonces = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryClientAccessNonceStore() {
        this(Clock.systemUTC());
    }

    public InMemoryClientAccessNonceStore(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean tryClaim(ClientAccessNonceKey key, Instant expiresAt) {
        Instant now = clock.instant();
        nonces.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
        return nonces.putIfAbsent(key, expiresAt) == null;
    }
}
