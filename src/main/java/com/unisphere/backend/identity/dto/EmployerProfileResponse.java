package com.unisphere.backend.identity.dto;

import java.time.LocalDateTime;

public record EmployerProfileResponse(
        Long id,
        String email,
        String role,
        String status,
        boolean isVerified,
        String avatarUrl,
        String phone,
        LocalDateTime createdAt,
        String companyName,
        String companyLogoUrl,
        String industry,
        String companySize,
        String websiteUrl,
        String description,
        boolean companyVerified
) implements UserProfileResponse {}
