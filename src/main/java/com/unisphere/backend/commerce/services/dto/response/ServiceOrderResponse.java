package com.unisphere.backend.commerce.services.dto.response;

import com.unisphere.backend.commerce.services.enums.ServiceOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ServiceOrderResponse(
        Long id,
        Long listingId,
        String listingTitle,
        ServiceProviderResponse provider,
        ServiceProviderResponse client,
        String requirements,
        BigDecimal agreedPrice,
        LocalDateTime scheduledAt,
        ServiceOrderStatus status,
        String decisionReason,
        Long conversationId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
