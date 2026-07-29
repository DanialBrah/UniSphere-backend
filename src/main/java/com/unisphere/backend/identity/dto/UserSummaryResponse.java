package com.unisphere.backend.identity.dto;

/**
 * {@code isFollowing} is relative to the requesting user, and is batch-resolved for a whole page
 * in one query — see {@code FollowRepository.findFollowedIdsAmong}. Without it a results list
 * could only learn follow state by toggling, so every follow button would render in the wrong
 * state until clicked.
 */
public record UserSummaryResponse(
        Long id,
        String displayName,
        String email,
        String role,
        String avatarUrl,
        boolean isFollowing
) {}
