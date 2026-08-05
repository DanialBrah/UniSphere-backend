package com.unisphere.backend.social.community.dto.response;

import java.time.LocalDateTime;

public record CommunityBanResponse(
        Long userId,
        String displayName,
        String avatarUrl,
        Long bannedBy,
        String reason,
        LocalDateTime bannedAt
) {}
