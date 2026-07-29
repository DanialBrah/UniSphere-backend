package com.unisphere.backend.identity.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * {@code refreshToken} is carried internally so the controller can place it in an HttpOnly cookie,
 * but is {@link JsonIgnore}d so it never reaches the response body — and therefore never becomes
 * readable by browser JavaScript. See {@code RefreshTokenCookie} for the reasoning.
 */
public record AuthResponse(
        String accessToken,
        @JsonIgnore String refreshToken,
        String tokenType,
        long expiresIn,
        UserProfileResponse user
) {
    public AuthResponse(String accessToken, String refreshToken, long expiresIn, UserProfileResponse user) {
        this(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}
