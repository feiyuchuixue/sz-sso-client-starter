package com.sz.ssoclient.clientaccess.state;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Shared atomic CAP state repository backed by Spring Data Redis. */
public final class RedisClientAccessStateRepository implements ClientAccessStateRepository {

    private static final DefaultRedisScript<Long> COMPARE_AND_SET = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if current == ARGV[1] then
                redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
                return 1
            end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if current == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    public RedisClientAccessStateRepository(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "redis");
    }

    @Override
    public String get(String key) {
        return redis.opsForValue().get(required(key));
    }

    @Override
    public boolean putIfAbsent(String key, String value, Duration ttl) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
                required(key), required(value), positive(ttl)));
    }

    @Override
    public boolean compareAndSet(String key, String expectedValue, String newValue, Duration ttl) {
        Long result = redis.execute(COMPARE_AND_SET, List.of(required(key)),
                required(expectedValue), required(newValue), Long.toString(positive(ttl).toMillis()));
        return Long.valueOf(1L).equals(result);
    }

    @Override
    public boolean compareAndDelete(String key, String expectedValue) {
        Long result = redis.execute(COMPARE_AND_DELETE, List.of(required(key)), required(expectedValue));
        return Long.valueOf(1L).equals(result);
    }

    @Override
    public boolean shared() {
        return true;
    }

    @Override
    public String description() {
        return "Spring Data Redis";
    }

    private static Duration positive(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative() || ttl.toMillis() <= 0) {
            throw new IllegalArgumentException("state ttl must be positive");
        }
        return ttl;
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("state key and value are required");
        }
        return value;
    }
}
