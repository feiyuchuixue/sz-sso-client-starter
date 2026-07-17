package com.sz.ssoclient.clientaccess.state;

import com.sz.ssoclient.clientaccess.inbound.ClientInboundEventStatus;
import com.sz.ssoclient.clientaccess.inbound.RepositoryClientAccessNonceStore;
import com.sz.ssoclient.clientaccess.inbound.RepositoryClientInboundEventStore;
import com.sz.ssoclient.clientaccess.json.ClientAccessJsonCodec;
import com.sz.ssoclient.clientaccess.web.ClientLoginTransaction;
import com.sz.ssoclient.clientaccess.web.ClientLoginTransactionException;
import com.sz.ssoclient.clientaccess.web.ClientLoginTransactionStatus;
import com.sz.ssoclient.clientaccess.web.RepositoryClientLoginTransactionStore;
import com.sz.ssoclient.pojo.SsoLoginResult;
import com.sz.ssocore.clientaccess.v1.ClientAccessDirection;
import com.sz.ssocore.clientaccess.v1.ClientAccessNonceKey;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositoryBackedClientAccessStoresTest {

    private static final Instant NOW = Instant.parse("2026-07-15T03:00:00Z");

    @Test
    void nonceClaimIsSharedAcrossInstancesAndExpires() {
        MutableClock clock = new MutableClock(NOW);
        ClientAccessStateRepository repository = new InMemoryClientAccessStateRepository(clock);
        RepositoryClientAccessNonceStore first = new RepositoryClientAccessNonceStore(repository, clock);
        RepositoryClientAccessNonceStore second = new RepositoryClientAccessNonceStore(repository, clock);
        ClientAccessNonceKey key = new ClientAccessNonceKey("1.0", ClientAccessDirection.SERVER_TO_CLIENT,
                "client-a", "nonce-1");

        assertThat(first.tryClaim(key, NOW.plusSeconds(30))).isTrue();
        assertThat(second.tryClaim(key, NOW.plusSeconds(30))).isFalse();

        clock.advance(Duration.ofSeconds(31));
        assertThat(second.tryClaim(key, NOW.plusSeconds(60))).isTrue();
    }

    @Test
    void inboundEventClaimAndCompletionAreVisibleAcrossInstances() {
        MutableClock clock = new MutableClock(NOW);
        ClientAccessStateRepository repository = new InMemoryClientAccessStateRepository(clock);
        RepositoryClientInboundEventStore first = new RepositoryClientInboundEventStore(repository, "client-a", clock);
        RepositoryClientInboundEventStore second = new RepositoryClientInboundEventStore(repository, "client-a", clock);

        assertThat(first.begin("message", "event-1", NOW.plusSeconds(300)))
                .isEqualTo(ClientInboundEventStatus.ACQUIRED);
        assertThat(second.begin("message", "event-1", NOW.plusSeconds(300)))
                .isEqualTo(ClientInboundEventStatus.IN_PROGRESS);

        first.complete("message", "event-1", NOW.plusSeconds(300));
        assertThat(second.begin("message", "event-1", NOW.plusSeconds(300)))
                .isEqualTo(ClientInboundEventStatus.COMPLETED);
    }

    @Test
    void inboundEventRetriesClaimWhenPreviousLeaseExpiresBetweenAtomicSetAndRead() {
        AtomicInteger claims = new AtomicInteger();
        ClientAccessStateRepository expiringRace = new ClientAccessStateRepository() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public boolean putIfAbsent(String key, String value, Duration ttl) {
                return claims.incrementAndGet() == 2;
            }

            @Override
            public boolean compareAndSet(String key, String expectedValue, String newValue, Duration ttl) {
                return false;
            }

            @Override
            public boolean compareAndDelete(String key, String expectedValue) {
                return false;
            }

            @Override
            public boolean shared() {
                return true;
            }

            @Override
            public String description() {
                return "lease-expiry-race";
            }
        };
        RepositoryClientInboundEventStore store = new RepositoryClientInboundEventStore(expiringRace,
                "client-a", Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(store.begin("message", "event-1", NOW.plusSeconds(300)))
                .isEqualTo(ClientInboundEventStatus.ACQUIRED);
        assertThat(claims).hasValue(2);
    }

    @Test
    void loginTransactionCreatedByOneInstanceCanBeCompletedByAnother() {
        MutableClock clock = new MutableClock(NOW);
        ClientAccessStateRepository repository = new InMemoryClientAccessStateRepository(clock);
        RepositoryClientLoginTransactionStore first = loginStore(repository, clock);
        RepositoryClientLoginTransactionStore second = loginStore(repository, clock);
        ClientLoginTransaction transaction = transaction("state-1", "browser-1");

        first.create(transaction, 1);
        ClientLoginTransaction exchanging = second.beginExchange("state-1", "browser-1", NOW);
        SsoLoginResult result = SsoLoginResult.of("local-token", 7200L, "local-user");
        second.complete("state-1", "browser-1", result);

        assertThat(exchanging.status()).isEqualTo(ClientLoginTransactionStatus.EXCHANGING);
        assertThat(first.beginExchange("state-1", "browser-1", NOW).status())
                .isEqualTo(ClientLoginTransactionStatus.COMPLETED);
        assertThat(first.find("state-1").completedResult().getAccessToken()).isEqualTo("local-token");
    }

    @Test
    void pendingLimitIsAtomicAcrossInstancesAndCompletedTransactionReleasesSlot() {
        MutableClock clock = new MutableClock(NOW);
        ClientAccessStateRepository repository = new InMemoryClientAccessStateRepository(clock);
        RepositoryClientLoginTransactionStore first = loginStore(repository, clock);
        RepositoryClientLoginTransactionStore second = loginStore(repository, clock);

        first.create(transaction("state-1", "browser-1"), 1);
        assertThatThrownBy(() -> second.create(transaction("state-2", "browser-1"), 1))
                .isInstanceOf(ClientLoginTransactionException.class)
                .hasMessageContaining("Too many pending");

        first.beginExchange("state-1", "browser-1", NOW);
        first.complete("state-1", "browser-1", SsoLoginResult.of("token", 60L, null));
        second.create(transaction("state-2", "browser-1"), 1);

        assertThat(second.find("state-2")).isNotNull();
    }

    @Test
    void concurrentExchangeCanBeClaimedByOnlyOneInstance() throws Exception {
        MutableClock clock = new MutableClock(NOW);
        ClientAccessStateRepository repository = new InMemoryClientAccessStateRepository(clock);
        RepositoryClientLoginTransactionStore first = loginStore(repository, clock);
        RepositoryClientLoginTransactionStore second = loginStore(repository, clock);
        first.create(transaction("state-1", "browser-1"), 5);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ClientLoginTransaction> firstResult = executor.submit(() -> {
                start.await();
                return first.beginExchange("state-1", "browser-1", NOW);
            });
            Future<ClientLoginTransaction> secondResult = executor.submit(() -> {
                start.await();
                return second.beginExchange("state-1", "browser-1", NOW);
            });
            start.countDown();

            int successes = successful(firstResult) + successful(secondResult);
            assertThat(successes).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void expiredTransactionDisappearsAcrossInstances() {
        MutableClock clock = new MutableClock(NOW);
        ClientAccessStateRepository repository = new InMemoryClientAccessStateRepository(clock);
        RepositoryClientLoginTransactionStore first = loginStore(repository, clock);
        RepositoryClientLoginTransactionStore second = loginStore(repository, clock);
        first.create(transaction("state-1", "browser-1"), 5);

        clock.advance(Duration.ofSeconds(601));

        assertThat(second.find("state-1")).isNull();
        assertThatThrownBy(() -> second.beginExchange("state-1", "browser-1", clock.instant()))
                .isInstanceOf(ClientLoginTransactionException.class)
                .hasMessageContaining("missing or expired");
    }

    @Test
    void sharedStoreFailureFailsClosedWithoutMemoryFallback() {
        ClientAccessStateRepository unavailable = new ClientAccessStateRepository() {
            @Override
            public String get(String key) {
                throw new IllegalStateException("shared store unavailable");
            }

            @Override
            public boolean putIfAbsent(String key, String value, Duration ttl) {
                throw new IllegalStateException("shared store unavailable");
            }

            @Override
            public boolean compareAndSet(String key, String expectedValue, String newValue, Duration ttl) {
                throw new IllegalStateException("shared store unavailable");
            }

            @Override
            public boolean compareAndDelete(String key, String expectedValue) {
                throw new IllegalStateException("shared store unavailable");
            }

            @Override
            public boolean shared() {
                return true;
            }

            @Override
            public String description() {
                return "unavailable-test-store";
            }
        };

        RepositoryClientAccessNonceStore store = new RepositoryClientAccessNonceStore(unavailable,
                Clock.fixed(NOW, ZoneOffset.UTC));
        ClientAccessNonceKey key = new ClientAccessNonceKey("1.0", ClientAccessDirection.SERVER_TO_CLIENT,
                "client-a", "nonce-1");

        assertThatThrownBy(() -> store.tryClaim(key, NOW.plusSeconds(30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shared store unavailable");
    }

    private static RepositoryClientLoginTransactionStore loginStore(
            ClientAccessStateRepository repository, Clock clock) {
        return new RepositoryClientLoginTransactionStore(repository, new ClientAccessJsonCodec(), clock,
                "client-a");
    }

    private static ClientLoginTransaction transaction(String stateHash, String browserHash) {
        return new ClientLoginTransaction(stateHash, browserHash, "client-a", "/", "auth-1",
                "exchange-idempotency-key", NOW, NOW.plusSeconds(600),
                ClientLoginTransactionStatus.CREATED, null);
    }

    private static int successful(Future<ClientLoginTransaction> result) throws Exception {
        try {
            assertThat(result.get().status()).isEqualTo(ClientLoginTransactionStatus.EXCHANGING);
            return 1;
        } catch (ExecutionException exception) {
            assertThat(exception.getCause()).isInstanceOf(ClientLoginTransactionException.class);
            return 0;
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
