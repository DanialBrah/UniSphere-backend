package com.unisphere.backend.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterClubRequest(
        @Email(message = "Must be a valid email address")
        @NotBlank(message = "Email is required")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8–72 characters")
        String password,

        @NotBlank(message = "Club name is required")
        @Size(max = 255)
        String name,

        Long universityId,

        @Size(max = 100)
        String category,

        String description,

        @Size(max = 20)
        String phone
) {}
