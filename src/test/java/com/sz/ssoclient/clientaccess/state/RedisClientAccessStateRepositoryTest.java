package com.sz.ssoclient.clientaccess.state;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisClientAccessStateRepositoryTest {

    @Test
    void usesAtomicRedisOperationsAndReportsSharedCapability() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("state-key", "value", Duration.ofSeconds(30))).thenReturn(true);
        when(redis.execute(any(RedisScript.class), eq(List.of("state-key")),
                eq("value"), eq("updated"), eq("30000"))).thenReturn(1L);
        when(redis.execute(any(RedisScript.class), eq(List.of("state-key")), eq("updated")))
                .thenReturn(1L);
        RedisClientAccessStateRepository repository = new RedisClientAccessStateRepository(redis);

        assertThat(repository.putIfAbsent("state-key", "value", Duration.ofSeconds(30))).isTrue();
        assertThat(repository.compareAndSet("state-key", "value", "updated", Duration.ofSeconds(30))).isTrue();
        assertThat(repository.compareAndDelete("state-key", "updated")).isTrue();
        assertThat(repository.shared()).isTrue();
        assertThat(repository.description()).isEqualTo("Spring Data Redis");

        verify(values).setIfAbsent("state-key", "value", Duration.ofSeconds(30));
    }
}
