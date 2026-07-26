package com.unisphere.backend.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs BEFORE {@code JwtAuthenticationFilter} — there is no authenticated principal yet, so this
 * tier limits by client IP. Scoped to every unauthenticated path in {@link RateLimitPaths#PUBLIC_PATHS}
 * (auth endpoints plus swagger/api-docs/ws) except {@code /api/health}, which is exempt entirely
 * — it's not a meaningful attack surface, and its global (Redis-shared) counter would otherwise
 * get hit by every scaled instance's health probe against the same budget, risking a healthy
 * deployment tripping 429s on itself. Everything else — every protected endpoint — is left to
 * {@link UserRateLimitFilter}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IpRateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        boolean isPublicPath = RateLimitPaths.PUBLIC_PATHS.stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, request.getServletPath()));

        if (!properties.isEnabled()
                || HttpMethod.OPTIONS.matches(request.getMethod())
                || !isPublicPath
                || "/api/health".equals(request.getServletPath())) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = "ratelimit:ip:" + ClientIpResolver.resolve(request);
        RateLimitProperties.Limit limit = properties.getAuth();

        if (!rateLimiterService.tryConsume(key, limit)) {
            log.warn("IP rate limit exceeded for key {}", key);
            RateLimitResponseWriter.writeTooManyRequests(
                    response, objectMapper, rateLimiterService.retryAfterSeconds(key, limit));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
