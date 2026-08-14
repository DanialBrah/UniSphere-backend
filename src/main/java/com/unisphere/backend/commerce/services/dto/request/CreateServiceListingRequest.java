package com.unisphere.backend.commerce.services.dto.request;

import com.unisphere.backend.commerce.services.enums.ServiceDeliveryMode;
import com.unisphere.backend.commerce.services.enums.ServicePricingType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A new service listing. Always created {@code ACTIVE} — there is no draft state (a listing is
 * either offered or paused, matching "showcase implies immediate visibility" reasoning
 * {@code projects.md} used for {@code Project}).
 *
 * <p>One cross-field rule cannot be expressed with bean validation and is enforced in
 * {@code ServiceListingService}, surfacing as 400 {@code BAD_REQUEST}: {@code pricingType} of
 * {@code FIXED}/{@code HOURLY} requires {@code price}; {@code NEGOTIABLE} forbids it.
 *
 * @param portfolioImageKey a key returned by {@code POST /services/media/presign} or {@code /upload},
 *                          owned by the caller — see {@code ServiceMediaService.assertOwnedKey}
 */
public record CreateServiceListingRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description,

        @NotBlank(message = "Category is required")
        @Size(max = 100, message = "Category must not exceed 100 characters")
        String category,

        /* Null means FIXED. */
        ServicePricingType pricingType,

        @DecimalMin(value = "0.0", message = "price must not be negative")
        BigDecimal price,

        /* Null means BOTH. */
        ServiceDeliveryMode deliveryMode,

        @Size(max = 500, message = "portfolioImageKey must not exceed 500 characters")
        String portfolioImageKey
) {}
