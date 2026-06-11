package com.unisphere.backend.social.messaging.dto.response;

public record TypingEvent(
        Long conversationId,
        Long userId,
        String displayName,
        boolean typing
) {}
