package com.unisphere.backend.common.ratelimit;

import java.util.List;

/**
 * Single source of truth for which paths don't require authentication — used both by
 * {@code SecurityConfig}'s {@code permitAll()} list and to decide the rate-limit tier: every path
 * here gets the strict IP-keyed tier ({@link IpRateLimitFilter}); everything else gets the
 * per-user tier ({@link UserRateLimitFilter}). Keeping both concerns on the same list means they
 * can never silently drift apart.
 */
public final class RateLimitPaths {

    private RateLimitPaths() {
    }

    public static final List<String> PUBLIC_PATHS = List.of(
            // Auth endpoints that don't require a token
            "/api/v1/auth/register/**",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            // Infrastructure
            "/api/health",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            // WebSocket handshake — JWT is validated inside JwtChannelInterceptor on STOMP CONNECT
            "/ws/**"
    );
}
