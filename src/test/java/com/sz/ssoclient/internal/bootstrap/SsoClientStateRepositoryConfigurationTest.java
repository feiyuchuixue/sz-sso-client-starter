package com.sz.ssoclient.internal.bootstrap;

import com.sz.ssoclient.spi.advanced.SsoClientStateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class SsoClientStateRepositoryConfigurationTest {

    @Test
    void inMemoryRepositoryProvidesTtlCasAndCompareDelete() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-04T00:00:00Z"));
        InMemorySsoClientStateRepository repository =
                new InMemorySsoClientStateRepository(clock);

        assertThat(repository.putIfAbsent("key", "v1", Duration.ofSeconds(10))).isTrue();
        assertThat(repository.putIfAbsent("key", "other", Duration.ofSeconds(10))).isFalse();
        assertThat(repository.compareAndSet(
                "key", "v1", "v2", Duration.ofSeconds(10))).isTrue();
        assertThat(repository.compareAndDelete("key", "wrong")).isFalse();
        assertThat(repository.compareAndDelete("key", "v2")).isTrue();

        assertThat(repository.putIfAbsent("expiring", "value", Duration.ofSeconds(1))).isTrue();
        clock.advance(Duration.ofSeconds(2));
        assertThat(repository.get("expiring")).isNull();
        assertThat(repository.shared()).isFalse();
    }

    @Test
    void selectsRedisInProductionAndRejectsProductionWithoutIt() {
        SsoClientInfrastructureConfiguration configuration =
                new SsoClientInfrastructureConfiguration();
        MockEnvironment production = new MockEnvironment().withProperty("spring.profiles.active", "prod");

        StaticListableBeanFactory withRedis = new StaticListableBeanFactory();
        withRedis.addBean("redis", mock(StringRedisTemplate.class));
        SsoClientStateRepository repository = configuration.ssoClientStateRepository(
                withRedis.getBeanProvider(StringRedisTemplate.class),
                production,
                Clock.systemUTC());
        assertThat(repository).isInstanceOf(RedisSsoClientStateRepository.class);
        assertThat(repository.shared()).isTrue();

        StaticListableBeanFactory withoutRedis = new StaticListableBeanFactory();
        assertThatThrownBy(() -> configuration.ssoClientStateRepository(
                withoutRedis.getBeanProvider(StringRedisTemplate.class),
                production,
                Clock.systemUTC()))
                .isInstanceOf(SsoClientConfigurationException.class)
                .hasMessageContaining("Redis")
                .hasMessageContaining("prod");
    }

    @Test
    void permitsInMemoryRepositoryOnlyForLocalDevelopmentOrTest() {
        SsoClientInfrastructureConfiguration configuration =
                new SsoClientInfrastructureConfiguration();
        StaticListableBeanFactory withoutRedis = new StaticListableBeanFactory();
        for (String profile : new String[]{"local", "dev", "test"}) {
            MockEnvironment environment = new MockEnvironment();
            environment.setActiveProfiles(profile);
            SsoClientStateRepository repository = configuration.ssoClientStateRepository(
                    withoutRedis.getBeanProvider(StringRedisTemplate.class),
                    environment,
                    Clock.systemUTC());
            assertThat(repository).isInstanceOf(InMemorySsoClientStateRepository.class);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
