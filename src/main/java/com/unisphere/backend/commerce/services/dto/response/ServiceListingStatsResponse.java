package com.unisphere.backend.commerce.services.dto.response;

/** Order status breakdown for one listing. Backs the provider's dashboard view. */
public record ServiceListingStatsResponse(
        Long pending,
        Long accepted,
        Long inProgress,
        Long completed,
        Long cancelled,
        Long disputed,
        int totalOrders
) {}
