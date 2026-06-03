package com.unisphere.backend.identity.dto;

import java.time.LocalDateTime;

public record AdminProfileResponse(
        Long id,
        String email,
        String role,
        String status,
        boolean isVerified,
        String avatarUrl,
        String phone,
        LocalDateTime createdAt,
        String fullName,
        String adminLevel
) implements UserProfileResponse {}
