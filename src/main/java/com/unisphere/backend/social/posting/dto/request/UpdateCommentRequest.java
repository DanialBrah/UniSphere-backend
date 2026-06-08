package com.unisphere.backend.social.posting.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCommentRequest(

        @NotBlank(message = "Comment content must not be blank")
        @Size(max = 2000)
        String content
) {}
