package com.unisphere.backend.identity.dto;

import java.time.LocalDateTime;

public record ClubProfileResponse(
        Long id,
        String email,
        String role,
        String status,
        boolean isVerified,
        String avatarUrl,
        String phone,
        LocalDateTime createdAt,
        String name,
        Long universityId,
        String description,
        String logoUrl,
        String category,
        boolean isOfficial
) implements UserProfileResponse {}
