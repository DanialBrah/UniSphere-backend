package com.unisphere.backend.social.messaging.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddMemberRequest(

        @NotNull
        @Positive
        Long userId
) {}
