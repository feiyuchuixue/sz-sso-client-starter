package com.sz.ssoclient.clientaccess.inbound;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/** Single-process event store; clusters must replace it with a shared atomic store. */
public class InMemoryClientInboundEventStore implements ClientInboundEventStore {

    private final ConcurrentHashMap<String, Entry> events = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryClientInboundEventStore() {
        this(Clock.systemUTC());
    }

    public InMemoryClientInboundEventStore(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ClientInboundEventStatus begin(String namespace, String eventId, Instant expiresAt) {
        String key = key(namespace, eventId);
        Instant now = clock.instant();
        AtomicReference<ClientInboundEventStatus> status = new AtomicReference<>();
        events.compute(key, (ignored, current) -> {
            if (current == null || !current.expiresAt().isAfter(now)) {
                status.set(ClientInboundEventStatus.ACQUIRED);
                return new Entry(false, expiresAt);
            }
            status.set(current.completed()
                    ? ClientInboundEventStatus.COMPLETED : ClientInboundEventStatus.IN_PROGRESS);
            return current;
        });
        return status.get();
    }

    @Override
    public void complete(String namespace, String eventId, Instant expiresAt) {
        events.put(key(namespace, eventId), new Entry(true, expiresAt));
    }

    @Override
    public void release(String namespace, String eventId) {
        events.computeIfPresent(key(namespace, eventId), (ignored, current) -> current.completed() ? current : null);
    }

    private static String key(String namespace, String eventId) {
        if (namespace == null || namespace.isBlank() || eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("event namespace and id are required");
        }
        return namespace + ':' + eventId;
    }

    private record Entry(boolean completed, Instant expiresAt) {
        private Entry {
            Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }
}
