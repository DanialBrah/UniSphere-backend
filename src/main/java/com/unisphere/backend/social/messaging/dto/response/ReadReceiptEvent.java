package com.unisphere.backend.social.messaging.dto.response;

import java.time.LocalDateTime;

public record ReadReceiptEvent(
        Long conversationId,
        Long lastReadMessageId,
        Long userId,
        LocalDateTime readAt
) {}
