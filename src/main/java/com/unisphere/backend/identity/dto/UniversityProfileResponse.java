package com.unisphere.backend.identity.dto;

import java.time.LocalDateTime;

public record UniversityProfileResponse(
        Long id,
        String email,
        String role,
        String status,
        boolean isVerified,
        String avatarUrl,
        String phone,
        LocalDateTime createdAt,
        String name,
        String shortName,
        String logoUrl,
        String websiteUrl,
        String address,
        String country,
        String state,
        boolean institutionVerified
) implements UserProfileResponse {}
