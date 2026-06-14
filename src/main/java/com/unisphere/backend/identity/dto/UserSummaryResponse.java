package com.unisphere.backend.identity.dto;

public record UserSummaryResponse(
        Long id,
        String displayName,
        String email,
        String role,
        String avatarUrl
) {}
