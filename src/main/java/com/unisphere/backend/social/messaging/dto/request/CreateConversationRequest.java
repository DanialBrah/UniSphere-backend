package com.unisphere.backend.social.messaging.dto.request;

import com.unisphere.backend.social.messaging.enums.ConversationType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateConversationRequest(

        @NotNull
        ConversationType type,

        @NotEmpty
        List<Long> participantIds,

        @Size(max = 255)
        String name
) {}
