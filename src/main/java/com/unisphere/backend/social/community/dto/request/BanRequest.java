package com.unisphere.backend.social.community.dto.request;

import jakarta.validation.constraints.Size;

public record BanRequest(
        @Size(max = 500, message = "Reason must not exceed 500 characters")
        String reason
) {}
