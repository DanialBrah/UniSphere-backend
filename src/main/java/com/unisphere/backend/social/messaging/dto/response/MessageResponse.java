package com.unisphere.backend.social.messaging.dto.response;

import com.unisphere.backend.social.messaging.enums.MessageType;

import java.time.LocalDateTime;

public record MessageResponse(
        Long id,
        Long conversationId,
        Long senderId,
        String senderName,
        String senderAvatar,
        String content,
        MessageType msgType,
        String mediaUrl,
        Long replyToId,
        LocalDateTime createdAt
) {}
