package com.unisphere.backend.social.posting.dto.request;

import com.unisphere.backend.social.posting.enums.PostVisibility;
import jakarta.validation.constraints.Size;

public record UpdatePostRequest(

        @Size(max = 255)
        String title,

        @Size(max = 5000)
        String content,

        PostVisibility visibility
) {}
