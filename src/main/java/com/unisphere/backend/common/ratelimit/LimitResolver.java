package com.unisphere.backend.common.ratelimit;

import org.springframework.util.AntPathMatcher;

/**
 * Resolves the first {@code ratelimit.overrides[*]} entry matching a path, falling back to the
 * tier's own default. Shared by both HTTP filters so a path-specific cap can be configured
 * regardless of which tier happens to own that path.
 */
final class LimitResolver {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private LimitResolver() {
    }

    static RateLimitProperties.Limit resolve(
            RateLimitProperties properties, String servletPath, RateLimitProperties.Limit fallback) {
        return properties.getOverrides().stream()
                .filter(override -> PATH_MATCHER.match(override.getPathPattern(), servletPath))
                .findFirst()
                .<RateLimitProperties.Limit>map(override -> override)
                .orElse(fallback);
    }
}
