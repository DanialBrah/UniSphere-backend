package com.unisphere.backend.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @Email(message = "Must be a valid email address")
        @NotBlank(message = "Email is required")
        String email
) {}
