package com.unisphere.backend.commerce.events.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EventMediaPresignRequest(

        @NotBlank
        String filename,

        @NotBlank
        @Pattern(regexp = "image/(jpeg|png|gif|webp)", message = "Unsupported content type")
        String contentType
) {}
