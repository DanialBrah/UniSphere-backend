package com.unisphere.backend.common.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Redis-backed fixed-window rate limiter. Correct across multiple app instances since the
 * increment-and-maybe-expire step runs as a single atomic Lua script server-side, not as two
 * separate round trips from the app.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;

    private static final RedisScript<Long> INCREMENT_SCRIPT = loadScript();

    private static RedisScript<Long> loadScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/fixed_window_rate_limiter.lua"));
        script.setResultType(Long.class);
        return script;
    }

    /**
     * @return true if the caller identified by {@code key} is within {@code limit} for the
     * current window. Fails open (returns true) if Redis is unreachable — a rate-limiter outage
     * must not take down the whole API.
     */
    public boolean tryConsume(String key, RateLimitProperties.Limit limit) {
        Long count = increment(key, limit.getWindowSeconds());
        return count == null || count <= limit.getLimit();
    }

    public long retryAfterSeconds(String key, RateLimitProperties.Limit limit) {
        try {
            Long ttl = redisTemplate.getExpire(key);
            return (ttl != null && ttl > 0) ? ttl : limit.getWindowSeconds();
        } catch (DataAccessException e) {
            return limit.getWindowSeconds();
        }
    }

    private Long increment(String key, long windowSeconds) {
        try {
            return redisTemplate.execute(INCREMENT_SCRIPT, List.of(key), String.valueOf(windowSeconds));
        } catch (DataAccessException e) {
            log.warn("Rate limiter backend unavailable, failing open [{}]: {}", key, e.getMessage());
            return null;
        }
    }
}
