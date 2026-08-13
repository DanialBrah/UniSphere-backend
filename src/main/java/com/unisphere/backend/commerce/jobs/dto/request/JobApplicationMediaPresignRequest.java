package com.unisphere.backend.commerce.jobs.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record JobApplicationMediaPresignRequest(

        @NotBlank
        String filename,

        @NotBlank
        @Pattern(
                regexp = "application/pdf|application/msword"
                        + "|application/vnd\\.openxmlformats-officedocument\\.wordprocessingml\\.document",
                message = "Unsupported content type")
        String contentType
) {}
