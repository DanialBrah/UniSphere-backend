package com.unisphere.backend.social.community.dto.response;

import com.unisphere.backend.social.community.enums.CommunityMemberRole;
import com.unisphere.backend.social.community.enums.CommunityVisibility;

import java.time.LocalDateTime;

public record CommunityResponse(
        Long id,
        Long createdBy,
        String creatorName,
        String creatorAvatarUrl,
        String name,
        String description,
        String bannerUrl,
        CommunityVisibility visibility,
        Long universityId,
        int memberCount,
        /** The caller's own role, or null if they are not a member. */
        CommunityMemberRole viewerRole,
        /** Whether the caller has a PENDING join request on this (PRIVATE) community. */
        boolean hasPendingJoinRequest,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
