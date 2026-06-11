package com.unisphere.backend.social.posting.dto.request;

import com.unisphere.backend.social.posting.enums.PostType;
import com.unisphere.backend.social.posting.enums.PostVisibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreatePostRequest(

        @Size(max = 255)
        String title,

        @Size(max = 5000, message = "Content must not exceed 5000 characters")
        String content,

        PostType postType,

        PostVisibility visibility,

        Long universityId,

        List<Long> taggedUserIds,

        @Size(max = 10, message = "A post may have at most 10 media files")
        List<@Valid MediaItem> media
) {
    public record MediaItem(@NotBlank String mediaKey, @NotBlank String mediaType) {}
}
