package com.unisphere.backend.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterAlumniRequest(
        @Email(message = "Must be a valid email address")
        @NotBlank(message = "Email is required")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8–72 characters")
        String password,

        @NotBlank(message = "Full name is required")
        @Size(max = 255)
        String fullName,

        Long universityId,

        @Size(max = 20)
        String phone,

        /** Four-digit year string e.g. "2020" */
        @Size(max = 4)
        String graduationYear,

        @Size(max = 100)
        String degree,

        @Size(max = 255)
        String major,

        @Size(max = 255)
        String currentCompany,

        @Size(max = 255)
        String currentPosition,

        @Size(max = 500)
        String linkedinUrl
) {}
