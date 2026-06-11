package com.unisphere.backend.social.messaging.dto.request;

import jakarta.validation.constraints.NotNull;

public record AddMemberRequest(

        @NotNull
        Long userId
) {}
