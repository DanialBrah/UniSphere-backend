package com.unisphere.backend.commerce.services.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ServiceMediaPresignRequest(

        @NotBlank
        String filename,

        @NotBlank
        @Pattern(regexp = "image/jpeg|image/png|image/webp", message = "Unsupported content type")
        String contentType
) {}
