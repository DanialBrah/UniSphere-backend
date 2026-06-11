package com.unisphere.backend.campus.chatbot.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(

        @NotBlank
        @Size(max = 2000, message = "Message must not exceed 2000 characters")
        String message
) {}
