package com.unisphere.backend.identity.dto;

import java.time.LocalDateTime;

public record AlumniProfileResponse(
        Long id,
        String email,
        String role,
        String status,
        boolean isVerified,
        String avatarUrl,
        String phone,
        LocalDateTime createdAt,
        String fullName,
        Long universityId,
        String graduationYear,
        String degree,
        String major,
        String currentCompany,
        String currentPosition,
        String linkedinUrl
) implements UserProfileResponse {}
