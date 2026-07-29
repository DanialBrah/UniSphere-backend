package com.unisphere.backend.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the application-relative path used for rate-limit pattern matching.
 * <p>
 * Deliberately NOT {@code getServletPath()}: that returns the full path only when the
 * DispatcherServlet is mapped at {@code /} (as in production), but is empty under MockMvc unless a
 * test sets it explicitly. Matching on it therefore made every pattern silently miss in
 * integration tests — the whole IP tier was skipped, and requests fell through to the far looser
 * user tier, which is why {@code RateLimitIntegrationTest} saw a 401 where it expected a 429.
 * <p>
 * {@code getRequestURI()} is populated consistently in all three environments; stripping the
 * context path keeps the configured patterns app-relative if one is ever introduced.
 */
final class RequestPath {

    private RequestPath() {
    }

    static String of(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "";
        }
        String contextPath = request.getContextPath();
        return (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath))
                ? uri.substring(contextPath.length())
                : uri;
    }
}
