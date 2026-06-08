package com.unisphere.backend.social.posting.dto.response;

import com.unisphere.backend.social.posting.enums.PostType;
import com.unisphere.backend.social.posting.enums.PostVisibility;

import java.time.LocalDateTime;
import java.util.List;

public record PostResponse(
        Long id,
        PostAuthorResponse author,
        String title,
        String content,
        PostType postType,
        PostVisibility visibility,
        Long universityId,
        boolean pinned,
        long likesCount,
        long viewsCount,
        long commentCount,
        boolean liked,
        boolean saved,
        List<PostMediaResponse> media,
        List<Long> taggedUserIds,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
