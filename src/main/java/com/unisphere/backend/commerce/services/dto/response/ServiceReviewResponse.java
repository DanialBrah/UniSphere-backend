package com.unisphere.backend.commerce.services.dto.response;

import java.time.LocalDateTime;

public record ServiceReviewResponse(
        Long id,
        Long orderId,
        ServiceProviderResponse reviewer,
        Long revieweeId,
        int rating,
        String comment,
        LocalDateTime createdAt
) {}
