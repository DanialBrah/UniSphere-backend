package com.unisphere.backend.campus.news.dto.response;

import com.unisphere.backend.campus.news.enums.NewsCategory;
import com.unisphere.backend.campus.news.enums.NewsStatus;
import com.unisphere.backend.campus.news.enums.NewsVisibility;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Feed / search / listing payload — NewsArticleResponse minus content and media, which are the
 * two heaviest fields and are never rendered in a list.
 */
public record NewsArticleSummaryResponse(
        Long id,
        NewsAuthorResponse author,
        String title,
        String summary,
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
        LocalDateTime publishedAt,
        LocalDateTime createdAt
) {}
