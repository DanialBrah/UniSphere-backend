package com.unisphere.backend.common.ratelimit;

import com.unisphere.backend.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real Redis (Testcontainers), rate limiting explicitly re-enabled — verifies the actual 429 path
 * end-to-end rather than just the filter logic in isolation (see IpRateLimitFilterTest for that).
 * Rate limiting is disabled by default in the test profile so the rest of the suite (which fires
 * many requests at /auth/*) doesn't flake against a live limit.
 */
class RateLimitIntegrationTest extends AbstractIntegrationTest {

    private static final int AUTH_LIMIT = 3;

    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static {
        redis.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("ratelimit.enabled", () -> "true");
        registry.add("ratelimit.auth.limit", () -> String.valueOf(AUTH_LIMIT));
        registry.add("ratelimit.auth.window-seconds", () -> "60");
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void loginBruteForce_exceedsAuthLimit_returns429WithRetryAfter() throws Exception {
        String body = """
                {"email":"nonexistent+ratelimit-test@test.com","password":"WrongPass123"}""";

        for (int i = 0; i < AUTH_LIMIT; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"));
    }
}
