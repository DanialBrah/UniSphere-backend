package com.unisphere.backend.campus.news.dto.request;

import com.unisphere.backend.campus.news.enums.NewsCategory;
import com.unisphere.backend.campus.news.enums.NewsStatus;
import com.unisphere.backend.campus.news.enums.NewsVisibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * There is deliberately no universityId field. CreatePostRequest has one and PostService
 * conditionally ignores it — a rule you have to re-read the service to trust. Omitting it makes
 * "university scope is derived from the author" unbypassable by construction.
 */
public record CreateNewsArticleRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @Size(max = 500, message = "Summary must not exceed 500 characters")
        String summary,

        @Size(max = 100_000, message = "Content must not exceed 100000 characters")
        String content,

        /** null -> GENERAL */
        NewsCategory category,

        /** null -> PUBLIC */
        NewsVisibility visibility,

        /** null -> DRAFT. ARCHIVED is rejected on create. */
        NewsStatus status,

        /** Interpreted in the server's timezone; set it to queue a DRAFT for auto-publish. */
        @Future(message = "scheduledAt must be in the future")
        LocalDateTime scheduledAt,

        /** Bare object key under news/{yourUserId}/ — ownership is verified server-side. */
        String coverImageKey,

        @Size(max = 20, message = "An article may carry at most 20 tags")
        List<@NotBlank(message = "Tags must not be blank")
             @Size(max = 100, message = "A tag must not exceed 100 characters") String> tags,

        @Size(max = 20, message = "An article may have at most 20 media files")
        List<@Valid MediaItem> media
) {
    public record MediaItem(
            @NotBlank(message = "mediaKey is required") String mediaKey,
            @NotBlank(message = "mediaType is required") String mediaType
    ) {}
}
