package com.sz.ssoclient.internal.login.transaction;

import com.sz.ssoclient.spi.advanced.SsoClientStateRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SsoLoginTransactionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");
    private static final String STATE = "A".repeat(43);

    @Test
    void createUsesExactSixtySecondTtlSecureStateAndHashedAssociations() {
        FakeStateRepository stateRepository = new FakeStateRepository();
        SsoLoginTransactionService service = service(stateRepository);

        SsoLoginTransactionService.Created created = service.create("browser-secret", "/dashboard");

        assertEquals(STATE, created.state());
        assertEquals(NOW.plusSeconds(60), created.expiresAt());
        assertEquals(Duration.ofSeconds(60), stateRepository.lastTtl);
        String encoded = stateRepository.values.values().iterator().next();
        assertFalse(encoded.contains(STATE));
        assertFalse(encoded.contains("browser-secret"));
        assertTrue(encoded.contains("/dashboard") == false);
    }

    @Test
    void onlyOneConsumerCanEnterExchangingAndAttemptAlwaysDeletes() {
        FakeStateRepository stateRepository = new FakeStateRepository();
        SsoLoginTransactionService service = service(stateRepository);
        service.create("browser-a", "/dashboard");

        String value = service.consume("browser-a", STATE, transaction -> {
            assertEquals(SsoLoginTransactionStatus.EXCHANGING, transaction.status());
            assertThrows(IllegalStateException.class,
                    () -> service.consume("browser-a", STATE, ignored -> "replayed"));
            return "ok";
        });

        assertEquals("ok", value);
        assertTrue(stateRepository.values.isEmpty());
        assertThrows(IllegalStateException.class,
                () -> service.consume("browser-a", STATE, ignored -> "replayed"));
    }

    @Test
    void failedExchangeIsDeletedAndNeverResetToCreated() {
        FakeStateRepository stateRepository = new FakeStateRepository();
        SsoLoginTransactionService service = service(stateRepository);
        service.create("browser-a", "/dashboard");
        IllegalStateException primary = new IllegalStateException("ticket rejected");

        IllegalStateException actual = assertThrows(IllegalStateException.class,
                () -> service.consume("browser-a", STATE, ignored -> {
                    throw primary;
                }));

        assertEquals(primary, actual);
        assertTrue(stateRepository.values.isEmpty());
        assertThrows(IllegalStateException.class,
                () -> service.consume("browser-a", STATE, ignored -> "retry"));
    }

    @Test
    void wrongBrowserFailsBeforeExchangeAndDoesNotConsumeLegitimateState() {
        FakeStateRepository stateRepository = new FakeStateRepository();
        SsoLoginTransactionService service = service(stateRepository);
        service.create("browser-a", "/dashboard");

        assertThrows(IllegalStateException.class,
                () -> service.consume("browser-b", STATE, ignored -> "wrong"));

        assertEquals("ok", service.consume("browser-a", STATE, ignored -> "ok"));
    }

    private static SsoLoginTransactionService service(FakeStateRepository repository) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        SsoLoginTransactionRepository transactionRepository = new SsoLoginTransactionRepository(
                repository,
                new SsoLoginTransactionCodec(),
                clock,
                "client-a");
        return new SsoLoginTransactionService(
                transactionRepository,
                new SsoSafeBackValidator(),
                clock,
                () -> STATE);
    }

    private static final class FakeStateRepository implements SsoClientStateRepository {
        private final Map<String, String> values = new ConcurrentHashMap<>();
        private Duration lastTtl;

        @Override
        public String get(String key) {
            return values.get(key);
        }

        @Override
        public boolean putIfAbsent(String key, String value, Duration ttl) {
            lastTtl = ttl;
            return values.putIfAbsent(key, value) == null;
        }

        @Override
        public boolean compareAndSet(String key, String expectedValue, String newValue, Duration ttl) {
            lastTtl = ttl;
            return values.replace(key, expectedValue, newValue);
        }

        @Override
        public boolean compareAndDelete(String key, String expectedValue) {
            return values.remove(key, expectedValue);
        }

        @Override
        public boolean shared() {
            return true;
        }

        @Override
        public String description() {
            return "test";
        }
    }
}
