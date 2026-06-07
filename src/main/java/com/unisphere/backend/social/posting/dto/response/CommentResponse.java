package com.unisphere.backend.social.posting.dto.response;

import java.time.LocalDateTime;

public record CommentResponse(
        Long id,
        Long postId,
        Long parentCommentId,
        PostAuthorResponse author,
        String content,
        long likesCount,
        long replyCount,
        boolean liked,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
