package com.unisphere.backend.commerce.jobs.dto.response;

/** Denormalised actor block — the applicant on a job application. Mirrors {@code EventOrganizerResponse}. */
public record JobApplicantResponse(
        Long id,
        String displayName,
        String avatarUrl,
        String role
) {}
