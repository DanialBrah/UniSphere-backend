package com.unisphere.backend.identity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(

        // ── Common ──────────────────────────────────────────────────────────
        // null = no change | "" (blank) = remove avatar | non-blank = set new B2 key
        @Size(max = 500) String avatarUrl,
        @Size(max = 20)  String phone,

        // ── Student / Alumni / Admin (display name) ──────────────────────────
        // Also maps to University.name and Club.name for those roles
        @Size(max = 200) String fullName,

        // ── Student ─────────────────────────────────────────────────────────
        String  faculty,
        String  program,
        @Min(1) @Max(10) Integer yearOfStudy,

        // ── Alumni ──────────────────────────────────────────────────────────
        String currentCompany,
        @Size(max = 200) String currentPosition,
        @Size(max = 500) String linkedinUrl,

        // ── Employer ────────────────────────────────────────────────────────
        @Size(max = 200)  String companyName,
        String            industry,
        String            companySize,
        @Size(max = 500)  String websiteUrl,
        @Size(max = 2000) String description,

        // ── University ──────────────────────────────────────────────────────
        @Size(max = 100) String shortName,
        String address,
        String country,
        String state,

        // ── Club ────────────────────────────────────────────────────────────
        String category

) {}
