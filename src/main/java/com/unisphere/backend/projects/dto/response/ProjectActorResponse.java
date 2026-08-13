package com.unisphere.backend.projects.dto.response;

/**
 * Denormalised actor block — reused for a project's owner, an application's applicant, and a
 * member roster row's user. Mirrors {@code JobApplicantResponse}.
 */
public record ProjectActorResponse(
        Long id,
        String displayName,
        String avatarUrl,
        String role
) {}
