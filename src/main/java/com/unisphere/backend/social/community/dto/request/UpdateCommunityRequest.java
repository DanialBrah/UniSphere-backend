package com.unisphere.backend.social.community.dto.request;

import com.unisphere.backend.social.community.enums.CommunityVisibility;
import jakarta.validation.constraints.Size;

public record UpdateCommunityRequest(

        @Size(max = 255)
        String name,

        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description,

        CommunityVisibility visibility,

        String bannerKey
) {}
