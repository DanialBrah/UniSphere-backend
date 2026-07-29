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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs AFTER {@code JwtAuthenticationFilter}, so an authenticated principal is available and this
 * tier can key by user instead of IP — avoiding the shared-NAT problem (e.g. a campus WiFi/dorm
 * full of students collapsing into one bucket under pure IP limiting). Falls back to IP if a
 * request reaches this filter with no authenticated principal (e.g. a missing/invalid token on a
 * protected endpoint — Spring Security's own authorization check rejects it shortly after, but
 * this filter runs first).
 * <p>
 * Covers every protected endpoint — anything NOT in {@link RateLimitPaths#PUBLIC_PATHS}, which is
 * handled entirely by {@link IpRateLimitFilter} instead — with per-path overrides (e.g. a
 * stricter cap on the chatbot endpoint, which calls a paid Gemini API).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRateLimitFilter extends OncePerRequestFilter {

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

        String path = RequestPath.of(request);
        boolean isPublicPath = RateLimitPaths.PUBLIC_PATHS.stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, path));

        if (!properties.isEnabled() || HttpMethod.OPTIONS.matches(request.getMethod()) || isPublicPath) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitProperties.Limit limit = LimitResolver.resolve(properties, path, properties.getApi());
        String key = "ratelimit:user:" + resolveSubject(request);

        if (!rateLimiterService.tryConsume(key, limit)) {
            log.warn("User rate limit exceeded for key {}", key);
            RateLimitResponseWriter.writeTooManyRequests(
                    response, objectMapper, rateLimiterService.retryAfterSeconds(key, limit));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveSubject(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails userDetails) {
            return "u:" + userDetails.getUsername();
        }
        return "ip:" + ClientIpResolver.resolve(request);
    }
}
