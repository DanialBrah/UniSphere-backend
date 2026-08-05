package com.unisphere.backend.social.community.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAnnouncementRequest(

        @NotBlank
        @Size(max = 255)
        String title,

        @NotBlank
        @Size(max = 5000, message = "Content must not exceed 5000 characters")
        String content
) {}
