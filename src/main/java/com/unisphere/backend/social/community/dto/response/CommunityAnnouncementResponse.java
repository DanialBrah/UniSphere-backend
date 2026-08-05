package com.unisphere.backend.social.community.dto.response;

import java.time.LocalDateTime;

public record CommunityAnnouncementResponse(
        Long id,
        Long communityId,
        Long authorId,
        String authorName,
        String authorAvatarUrl,
        String title,
        String content,
        boolean pinned,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
