package com.unisphere.backend.identity.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record StudentProfileResponse(
        Long id,
        String email,
        String role,
        String status,
        boolean isVerified,
        String avatarUrl,
        String phone,
        LocalDateTime createdAt,
        String fullName,
        String matricNumber,
        String universityEmail,
        Long universityId,
        String faculty,
        String program,
        Integer yearOfStudy,
        LocalDate enrollmentDate,
        LocalDate expectedGraduation
) implements UserProfileResponse {}
