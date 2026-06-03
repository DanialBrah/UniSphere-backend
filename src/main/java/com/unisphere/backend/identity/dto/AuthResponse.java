package com.unisphere.backend.identity.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserProfileResponse user
) {
    public AuthResponse(String accessToken, String refreshToken, long expiresIn, UserProfileResponse user) {
        this(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}
