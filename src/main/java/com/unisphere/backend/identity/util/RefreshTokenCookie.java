package com.unisphere.backend.identity.util;

import com.unisphere.backend.config.JwtConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Builds the {@code Set-Cookie} header carrying the refresh token.
 * <p>
 * The refresh token is deliberately never returned in a JSON body (see {@code AuthResponse}) —
 * anything readable by JavaScript is exfiltratable by a single XSS hole or one compromised npm
 * dependency, and a 7-day refresh token is a far more valuable theft target than a 15-minute
 * access token. {@code HttpOnly} puts it out of reach of JS entirely.
 * <p>
 * {@code Path} is scoped to the auth endpoints so the browser never attaches it to the ~100 other
 * API calls that have no use for it. {@code SameSite} is the CSRF defence: with the default
 * {@code Lax}, the cookie is not sent on cross-site POSTs, so an attacker's page cannot silently
 * mint tokens against {@code /auth/refresh}. It is configurable because a deployment that serves
 * the SPA from a genuinely different site than the API (e.g. netlify.app vs onrender.com, rather
 * than app/api subdomains of one domain) requires {@code None} — which in turn requires
 * {@code Secure}, hence that being configurable too.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookie {

    public static final String NAME = "refresh_token";
    private static final String PATH = "/api/v1/auth";

    private final JwtConfig jwtConfig;

    @Value("${app.auth.cookie.secure:false}")
    private boolean secure;

    @Value("${app.auth.cookie.same-site:Lax}")
    private String sameSite;

    /** Set-Cookie value that stores the refresh token for its full server-side lifetime. */
    public String build(String refreshToken) {
        return base(refreshToken)
                .maxAge(Duration.ofMillis(jwtConfig.getRefreshExpiration()))
                .build()
                .toString();
    }

    /** Set-Cookie value that expires the cookie immediately — used on logout. */
    public String clear() {
        return base("").maxAge(0).build().toString();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(PATH);
    }
}
