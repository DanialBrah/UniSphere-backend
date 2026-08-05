package com.unisphere.backend.campus.news.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateNewsCommentRequest(

        @NotBlank(message = "Comment content must not be blank")
        @Size(max = 2000)
        String content,

        Long parentCommentId
) {}
