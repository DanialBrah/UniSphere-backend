package com.unisphere.backend.commerce.services.dto.request;

import com.unisphere.backend.commerce.services.enums.ServiceDeliveryMode;
import com.unisphere.backend.commerce.services.enums.ServicePricingType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Patch semantics throughout: a null field leaves the current value untouched; a blank string
 * clears an optional text field. {@code price}/{@code portfolioImageKey} need an explicit clear
 * flag since {@code null} is ambiguous between "don't touch" and "clear" for a non-string type —
 * same trick as {@code UpdateJobRequest.clearSalary}.
 */
public record UpdateServiceListingRequest(

        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description,

        @Size(max = 100, message = "Category must not exceed 100 characters")
        String category,

        ServicePricingType pricingType,

        @DecimalMin(value = "0.0", message = "price must not be negative")
        BigDecimal price,

        Boolean clearPrice,

        ServiceDeliveryMode deliveryMode,

        @Size(max = 500, message = "portfolioImageKey must not exceed 500 characters")
        String portfolioImageKey,

        Boolean clearPortfolioImageKey
) {}
