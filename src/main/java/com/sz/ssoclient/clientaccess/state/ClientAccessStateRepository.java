package com.sz.ssoclient.clientaccess.state;

import java.time.Duration;

/** Atomic key-value boundary used by CAP state stores. */
public interface ClientAccessStateRepository {

    String get(String key);

    boolean putIfAbsent(String key, String value, Duration ttl);

    boolean compareAndSet(String key, String expectedValue, String newValue, Duration ttl);

    boolean compareAndDelete(String key, String expectedValue);

    boolean shared();

    String description();
}
