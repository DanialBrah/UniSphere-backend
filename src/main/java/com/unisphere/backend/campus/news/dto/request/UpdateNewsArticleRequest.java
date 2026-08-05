package com.unisphere.backend.campus.news.dto.request;

import com.unisphere.backend.campus.news.enums.NewsCategory;
import com.unisphere.backend.campus.news.enums.NewsVisibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Partial update — every field is optional and null means "leave alone".
 *
 * <p>status is deliberately absent: transitions go through PATCH /{articleId}/status so the
 * publish rules live in exactly one place.
 */
public record UpdateNewsArticleRequest(

        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @Size(max = 500, message = "Summary must not exceed 500 characters")
        String summary,

        @Size(max = 100_000, message = "Content must not exceed 100000 characters")
        String content,

        NewsCategory category,

        NewsVisibility visibility,

        String coverImageKey,

        /** Non-null replaces the whole tag set; an empty list clears it. */
        @Size(max = 20, message = "An article may carry at most 20 tags")
        List<@NotBlank(message = "Tags must not be blank")
             @Size(max = 100, message = "A tag must not exceed 100 characters") String> tags,

        List<Long> removeMediaIds,

        @Size(max = 20, message = "An article may have at most 20 media files")
        List<@Valid MediaItem> addMedia
) {
    public record MediaItem(
            @NotBlank(message = "mediaKey is required") String mediaKey,
            @NotBlank(message = "mediaType is required") String mediaType
    ) {}
}
