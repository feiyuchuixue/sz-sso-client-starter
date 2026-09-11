package com.sz.ssoclient.internal.login.transaction;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/** 创建并原子单次消费 60 秒 Browser 登录事务。 */
public final class SsoLoginTransactionService {

    public static final Duration TRANSACTION_TTL = Duration.ofSeconds(60);
    private static final int STATE_BYTES = 32;
    private static final int MAX_STATE_ATTEMPTS = 3;

    private final SsoLoginTransactionRepository repository;
    private final SsoSafeBackValidator backValidator;
    private final Clock clock;
    private final Supplier<String> stateSupplier;

    public SsoLoginTransactionService(
            SsoLoginTransactionRepository repository,
            SsoSafeBackValidator backValidator,
            Clock clock) {
        this(repository, backValidator, clock, SsoLoginTransactionService::secureState);
    }

    SsoLoginTransactionService(
            SsoLoginTransactionRepository repository,
            SsoSafeBackValidator backValidator,
            Clock clock,
            Supplier<String> stateSupplier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.backValidator = Objects.requireNonNull(backValidator, "backValidator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.stateSupplier = Objects.requireNonNull(stateSupplier, "stateSupplier");
    }

    public Created create(String browserId, String back) {
        String browserHash = SsoStateHasher.sha256(requireText(browserId, "browserId"));
        String safeBack = backValidator.validate(back);
        for (int attempt = 0; attempt < MAX_STATE_ATTEMPTS; attempt++) {
            String state = requireSecureState(stateSupplier.get());
            Instant createdAt = clock.instant();
            SsoLoginTransaction transaction = new SsoLoginTransaction(
                    SsoStateHasher.sha256(state),
                    browserHash,
                    safeBack,
                    createdAt,
                    createdAt.plus(TRANSACTION_TTL),
                    SsoLoginTransactionStatus.CREATED);
            if (repository.create(transaction)) {
                return new Created(state, transaction.back(), transaction.expiresAt());
            }
        }
        throw new IllegalStateException("无法创建唯一 SSO 登录 state");
    }

    public <T> T consume(
            String browserId,
            String state,
            Function<SsoLoginTransaction, T> exchange) {
        Objects.requireNonNull(exchange, "exchange");
        String stateHash = SsoStateHasher.sha256(requireText(state, "state"));
        String browserHash = SsoStateHasher.sha256(requireText(browserId, "browserId"));
        SsoLoginTransactionRepository.Stored stored = repository.read(stateHash);
        if (stored == null) {
            throw invalid("登录事务不存在、已过期或已消费");
        }
        SsoLoginTransaction current = stored.transaction();
        if (!current.expiresAt().isAfter(clock.instant())) {
            repository.compareAndDelete(stored);
            throw invalid("登录事务不存在、已过期或已消费");
        }
        if (!constantTimeEquals(current.browserHash(), browserHash)) {
            throw invalid("登录事务不属于当前 Browser");
        }
        if (current.status() != SsoLoginTransactionStatus.CREATED) {
            throw invalid("登录事务正在交换或已消费");
        }
        SsoLoginTransaction exchanging = current.exchanging();
        if (!repository.compareAndSet(stored, exchanging)) {
            throw invalid("登录事务已被并发消费");
        }
        SsoLoginTransactionRepository.Stored exchangingStored = repository.read(stateHash);
        if (exchangingStored == null
                || exchangingStored.transaction().status() != SsoLoginTransactionStatus.EXCHANGING) {
            throw invalid("登录事务交换状态丢失");
        }

        RuntimeException primaryFailure = null;
        try {
            return exchange.apply(exchanging);
        } catch (RuntimeException exception) {
            primaryFailure = exception;
            throw exception;
        } finally {
            boolean deleted = repository.compareAndDelete(exchangingStored);
            if (!deleted) {
                IllegalStateException cleanupFailure =
                        new IllegalStateException("SSO 登录事务消费后原子删除失败");
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(cleanupFailure);
                } else {
                    throw cleanupFailure;
                }
            }
        }
    }

    private static String secureState() {
        byte[] bytes = new byte[STATE_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String requireSecureState(String state) {
        String value = requireText(state, "state");
        if (value.length() < 43) {
            throw new IllegalStateException("SSO 登录 state 必须至少包含 256 bit 安全随机量");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }

    private static boolean constantTimeEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(
                left.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                right.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException(message);
    }

    public record Created(String state, String back, Instant expiresAt) {
        public Created {
            requireText(state, "state");
            requireText(back, "back");
            Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }
}
