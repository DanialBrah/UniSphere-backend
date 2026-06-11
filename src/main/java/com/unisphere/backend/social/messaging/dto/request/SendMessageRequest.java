package com.unisphere.backend.social.messaging.dto.request;

import com.unisphere.backend.social.messaging.enums.MessageType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(

        @NotNull
        Long conversationId,

        @Size(max = 5000)
        String content,

        MessageType msgType,

        @Size(max = 500)
        String mediaUrl,

        @Positive
        Long replyToId
) {}
