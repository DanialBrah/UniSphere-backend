package com.unisphere.backend.social.community.dto.response;

import com.unisphere.backend.social.community.enums.JoinRequestStatus;

import java.time.LocalDateTime;

public record CommunityJoinRequestResponse(
        Long id,
        Long userId,
        String displayName,
        String avatarUrl,
        JoinRequestStatus status,
        String message,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt
) {}
