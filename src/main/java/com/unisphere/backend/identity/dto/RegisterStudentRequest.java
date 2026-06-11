package com.unisphere.backend.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RegisterStudentRequest(
        @Email(message = "Must be a valid email address")
        @NotBlank(message = "Email is required")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8–72 characters")
        String password,

        @NotBlank(message = "Full name is required")
        @Size(max = 255)
        String fullName,

        @NotBlank(message = "Matric number is required")
        @Size(max = 50)
        String matricNumber,

        @Email(message = "University email must be valid")
        String universityEmail,

        Long universityId,

        @Size(max = 20)
        String phone,

        String faculty,
        String program,
        @Min(1) @Max(10) Integer yearOfStudy,
        LocalDate enrollmentDate,
        LocalDate expectedGraduation
) {}
