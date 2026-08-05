package com.unisphere.backend.social.community.dto.response;

import com.unisphere.backend.social.community.enums.CommunityMemberRole;

import java.time.LocalDateTime;

public record CommunityMemberResponse(
        Long userId,
        String displayName,
        String avatarUrl,
        CommunityMemberRole role,
        LocalDateTime joinedAt
) {}
