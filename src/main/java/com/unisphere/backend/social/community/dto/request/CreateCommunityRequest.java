package com.unisphere.backend.social.community.dto.request;

import com.unisphere.backend.social.community.enums.CommunityVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommunityRequest(

        @NotBlank
        @Size(max = 255)
        String name,

        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description,

        CommunityVisibility visibility,

        String bannerKey,

        Long universityId
) {}
