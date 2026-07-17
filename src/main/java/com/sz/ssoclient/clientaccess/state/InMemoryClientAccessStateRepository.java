package com.sz.ssoclient.clientaccess.state;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Local-only CAP state repository for development and tests. */
public class InMemoryClientAccessStateRepository implements ClientAccessStateRepository {

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryClientAccessStateRepository(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String get(String key) {
        AtomicReference<String> value = new AtomicReference<>();
        Instant now = clock.instant();
        entries.compute(required(key), (ignored, current) -> {
            if (expired(current, now)) {
                return null;
            }
            value.set(current.value());
            return current;
        });
        return value.get();
    }

    @Override
    public boolean putIfAbsent(String key, String value, Duration ttl) {
        AtomicBoolean created = new AtomicBoolean();
        Instant now = clock.instant();
        Entry replacement = new Entry(required(value), expiresAt(now, ttl));
        entries.compute(required(key), (ignored, current) -> {
            if (expired(current, now)) {
                created.set(true);
                return replacement;
            }
            return current;
        });
        return created.get();
    }

    @Override
    public boolean compareAndSet(String key, String expectedValue, String newValue, Duration ttl) {
        AtomicBoolean updated = new AtomicBoolean();
        Instant now = clock.instant();
        Entry replacement = new Entry(required(newValue), expiresAt(now, ttl));
        entries.compute(required(key), (ignored, current) -> {
            if (expired(current, now)) {
                return null;
            }
            if (Objects.equals(current.value(), expectedValue)) {
                updated.set(true);
                return replacement;
            }
            return current;
        });
        return updated.get();
    }

    @Override
    public boolean compareAndDelete(String key, String expectedValue) {
        AtomicBoolean deleted = new AtomicBoolean();
        Instant now = clock.instant();
        entries.compute(required(key), (ignored, current) -> {
            if (expired(current, now)) {
                return null;
            }
            if (Objects.equals(current.value(), expectedValue)) {
                deleted.set(true);
                return null;
            }
            return current;
        });
        return deleted.get();
    }

    @Override
    public boolean shared() {
        return false;
    }

    @Override
    public String description() {
        return "single-process memory";
    }

    private static boolean expired(Entry entry, Instant now) {
        return entry == null || !entry.expiresAt().isAfter(now);
    }

    private static Instant expiresAt(Instant now, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("state ttl must be positive");
        }
        return now.plus(ttl);
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("state key and value are required");
        }
        return value;
    }

    private record Entry(String value, Instant expiresAt) {
        private Entry {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }
}
