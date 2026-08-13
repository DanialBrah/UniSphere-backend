package com.unisphere.backend.commerce.events.dto.request;

import jakarta.validation.constraints.NotBlank;

public record EventCheckInRequest(

        @NotBlank(message = "ticketCode is required")
        String ticketCode
) {}
