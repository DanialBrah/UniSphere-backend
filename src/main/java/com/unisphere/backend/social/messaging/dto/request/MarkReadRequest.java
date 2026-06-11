package com.unisphere.backend.social.messaging.dto.request;

import jakarta.validation.constraints.NotNull;

public record MarkReadRequest(

        @NotNull
        Long conversationId,

        @NotNull
        Long lastReadMessageId
) {}
