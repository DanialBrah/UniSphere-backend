package com.unisphere.backend.social.posting.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MediaPresignRequest(

        @NotBlank
        String filename,

        @NotBlank
        @Pattern(regexp = "image/(jpeg|png|gif|webp)|video/(mp4|quicktime)",
                message = "Unsupported content type")
        String contentType
) {}
