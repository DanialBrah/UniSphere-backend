package com.unisphere.backend.commerce.services.dto.response;

import com.unisphere.backend.commerce.services.enums.ServiceDeliveryMode;
import com.unisphere.backend.commerce.services.enums.ServiceListingStatus;
import com.unisphere.backend.commerce.services.enums.ServicePricingType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Full detail view of a service listing.
 *
 * @param canModify whether the caller may edit, change status or delete this listing
 */
public record ServiceListingResponse(
        Long id,
        ServiceProviderResponse provider,
        String title,
        String description,
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
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
