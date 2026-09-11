package com.sz.ssoclient.internal.login.transaction;

import com.sz.ssoclient.spi.advanced.SsoClientStateRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/** 在高级原子状态 SPI 上实现登录事务的编码、key 隔离和 CAS。 */
public final class SsoLoginTransactionRepository {

    private static final String ROOT = "sz:sso:client:login:transaction:";

    private final SsoClientStateRepository repository;
    private final SsoLoginTransactionCodec codec;
    private final Clock clock;
    private final String clientPrefix;

    public SsoLoginTransactionRepository(
            SsoClientStateRepository repository,
            SsoLoginTransactionCodec codec,
            Clock clock,
            String clientFlag) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (clientFlag == null || clientFlag.isBlank()) {
            throw new IllegalArgumentException("clientFlag 不能为空");
        }
        this.clientPrefix = SsoStateHasher.sha256(clientFlag).substring(0, 16);
    }

    public boolean create(SsoLoginTransaction transaction) {
        return repository.putIfAbsent(
                key(transaction.stateHash()),
                codec.encode(transaction),
                ttl(transaction));
    }

    public Stored read(String stateHash) {
        String encoded = repository.get(key(stateHash));
        return encoded == null ? null : new Stored(encoded, codec.decode(encoded));
    }

    public boolean compareAndSet(Stored current, SsoLoginTransaction updated) {
        return repository.compareAndSet(
                key(updated.stateHash()),
                current.encoded(),
                codec.encode(updated),
                ttl(updated));
    }

    public boolean compareAndDelete(Stored current) {
        return repository.compareAndDelete(
                key(current.transaction().stateHash()),
                current.encoded());
    }

    private String key(String stateHash) {
        return ROOT + '{' + clientPrefix + "}:" + stateHash;
    }

    private Duration ttl(SsoLoginTransaction transaction) {
        Duration ttl = Duration.between(clock.instant(), transaction.expiresAt());
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalStateException("登录事务已过期");
        }
        return ttl;
    }

    public record Stored(String encoded, SsoLoginTransaction transaction) {
        public Stored {
            Objects.requireNonNull(encoded, "encoded");
            Objects.requireNonNull(transaction, "transaction");
        }
    }
}
