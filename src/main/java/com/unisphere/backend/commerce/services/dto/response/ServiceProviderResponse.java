package com.unisphere.backend.commerce.services.dto.response;

/** Denormalised actor block — the provider who posted a listing. Mirrors {@code JobApplicantResponse}. */
public record ServiceProviderResponse(
        Long id,
        String displayName,
        String avatarUrl,
        String role
) {}
