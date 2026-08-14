package com.unisphere.backend.commerce.services.dto.response;

import com.unisphere.backend.commerce.services.enums.ServiceDeliveryMode;
import com.unisphere.backend.commerce.services.enums.ServiceListingStatus;
import com.unisphere.backend.commerce.services.enums.ServicePricingType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** List-view shape — trims {@code description}, same as {@code JobSummaryResponse}. */
public record ServiceListingSummaryResponse(
        Long id,
        ServiceProviderResponse provider,
        String title,
        String category,
        ServicePricingType pricingType,
        BigDecimal price,
        ServiceDeliveryMode deliveryMode,
        String portfolioImageUrl,
        BigDecimal ratingAvg,
        int ratingCount,
        ServiceListingStatus status,
        Long universityId,
        boolean canModify,
        LocalDateTime createdAt
) {}
