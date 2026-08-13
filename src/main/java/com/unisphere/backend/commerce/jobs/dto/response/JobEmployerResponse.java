package com.unisphere.backend.commerce.jobs.dto.response;

/**
 * Denormalised actor block — the employer who posted a job. {@code companyLogoUrl} is resolved
 * through {@code MediaUrlResolver}, same as {@code EventOrganizerResponse.avatarUrl}.
 */
public record JobEmployerResponse(
        Long employerId,
        String companyName,
        String companyLogoUrl,
        String industry,
        String companySize,
        String websiteUrl,
        boolean companyVerified
) {}
