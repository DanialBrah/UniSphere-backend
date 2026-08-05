package com.unisphere.backend.campus.news.dto.response;

import com.unisphere.backend.campus.news.enums.NewsCategory;
import com.unisphere.backend.campus.news.enums.NewsStatus;
import com.unisphere.backend.campus.news.enums.NewsVisibility;

import java.time.LocalDateTime;
import java.util.List;

/** Full article payload, for the single-article read. Feeds use NewsArticleSummaryResponse. */
public record NewsArticleResponse(
        Long id,
        NewsAuthorResponse author,
        String title,
        String summary,
        String content,
        /** Presigned GET URL minted per read — the entity stores a bare key. */
        String coverImageUrl,
        NewsCategory category,
        NewsStatus status,
        NewsVisibility visibility,
        Long universityId,
        boolean featured,
        long viewsCount,
        long likesCount,
        long commentCount,
        boolean liked,
        boolean saved,
        List<String> tags,
        List<NewsMediaResponse> media,
        LocalDateTime publishedAt,
        LocalDateTime scheduledAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
