package com.unisphere.backend.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterUniversityRequest(
        @Email(message = "Must be a valid email address")
        @NotBlank(message = "Email is required")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8–72 characters")
        String password,

        @NotBlank(message = "University name is required")
        @Size(max = 255)
        String name,

        @Size(max = 50)
        String shortName,

        @Size(max = 500)
        String websiteUrl,

        @Size(max = 500)
        String address,

        @Size(max = 100)
        String country,

        @Size(max = 100)
        String state,

        @Size(max = 20)
        String phone
) {}
