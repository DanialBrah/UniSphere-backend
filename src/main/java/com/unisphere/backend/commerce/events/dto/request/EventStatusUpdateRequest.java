package com.unisphere.backend.commerce.events.dto.request;

import com.unisphere.backend.commerce.events.enums.EventStatus;
import jakarta.validation.constraints.NotNull;

public record EventStatusUpdateRequest(

        @NotNull(message = "status is required")
        EventStatus status
) {}
