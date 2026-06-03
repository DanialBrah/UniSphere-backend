package com.unisphere.backend.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token is required")
        @Size(max = 4096, message = "Refresh token is too long")
        String refreshToken
) {}
