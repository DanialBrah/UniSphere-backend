package com.unisphere.backend.campus.chatbot.dto.response;

import java.time.LocalDateTime;

public record ChatResponse(
        String reply,
        boolean fromCache,
        LocalDateTime timestamp
) {}
