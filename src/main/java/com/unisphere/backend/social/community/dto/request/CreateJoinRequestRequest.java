package com.unisphere.backend.social.community.dto.request;

import jakarta.validation.constraints.Size;

public record CreateJoinRequestRequest(
        @Size(max = 500, message = "Message must not exceed 500 characters")
        String message
) {}
