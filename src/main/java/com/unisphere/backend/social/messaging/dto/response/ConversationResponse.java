package com.unisphere.backend.social.messaging.dto.response;

import com.unisphere.backend.social.messaging.enums.ConversationType;

import java.time.LocalDateTime;
import java.util.List;

public record ConversationResponse(
        Long id,
        ConversationType convType,
        String name,
        List<MemberSummary> members,
        MessageResponse lastMessage,
        LocalDateTime createdAt
) {}
