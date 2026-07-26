package com.unisphere.backend.common.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    StringRedisTemplate redisTemplate;

    RateLimiterService service;

    private static final RateLimitProperties.Limit LIMIT = new RateLimitProperties.Limit(5, 60);

    @BeforeEach
    void setUp() {
        service = new RateLimiterService(redisTemplate);
    }

    @Test
    void underLimit_allowsRequest() {
        stubScriptResult(3L);

        assertThat(service.tryConsume("key", LIMIT)).isTrue();
    }

    @Test
    void atLimit_allowsRequest() {
        stubScriptResult(5L);

        assertThat(service.tryConsume("key", LIMIT)).isTrue();
    }

    @Test
    void overLimit_deniesRequest() {
        stubScriptResult(6L);

        assertThat(service.tryConsume("key", LIMIT)).isFalse();
    }

    @Test
    void redisThrows_failsOpen() {
        stubScriptError(new QueryTimeoutException("Redis Cloud unreachable"));

        assertThat(service.tryConsume("key", LIMIT)).isTrue();
    }

    @Test
    void redisReturnsNull_failsOpen() {
        stubScriptResult(null);

        assertThat(service.tryConsume("key", LIMIT)).isTrue();
    }

    @Test
    void retryAfterSeconds_usesRedisTtlWhenPositive() {
        when(redisTemplate.getExpire("key")).thenReturn(37L);

        assertThat(service.retryAfterSeconds("key", LIMIT)).isEqualTo(37L);
    }

    @Test
    void retryAfterSeconds_fallsBackToWindowWhenTtlMissing() {
        when(redisTemplate.getExpire("key")).thenReturn(null);

        assertThat(service.retryAfterSeconds("key", LIMIT)).isEqualTo(LIMIT.getWindowSeconds());
    }

    @Test
    void retryAfterSeconds_fallsBackToWindowWhenRedisThrows() {
        when(redisTemplate.getExpire("key")).thenThrow(new QueryTimeoutException("Redis Cloud unreachable"));

        assertThat(service.retryAfterSeconds("key", LIMIT)).isEqualTo(LIMIT.getWindowSeconds());
    }

    @SuppressWarnings("unchecked")
    private void stubScriptResult(Long value) {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(value);
    }

    @SuppressWarnings("unchecked")
    private void stubScriptError(RuntimeException error) {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenThrow(error);
    }
}
