package com.unisphere.backend.campus.news.dto.response;

import java.time.LocalDateTime;

public record NewsCommentResponse(
        Long id,
        Long articleId,
        Long parentCommentId,
        NewsAuthorResponse author,
        String content,
        long likesCount,
        long replyCount,
        boolean liked,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
