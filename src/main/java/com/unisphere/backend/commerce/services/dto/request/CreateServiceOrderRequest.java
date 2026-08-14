package com.unisphere.backend.commerce.services.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A client's formal request against a listing. {@code proposedPrice} only matters for
 * {@code NEGOTIABLE} listings — for {@code FIXED}/{@code HOURLY} listings
 * {@code ServiceOrderService.createOrder} always copies the listing's own price and ignores this
 * field. For {@code NEGOTIABLE} listings, the recommended flow is to agree a figure over the
 * {@code POST /services/{id}/inquire} conversation first, then supply it here — but it may be left
 * null and filled in later by the provider at accept time (see {@code ServiceOrderStatusUpdateRequest}).
 */
public record CreateServiceOrderRequest(

        @Size(max = 5000, message = "Requirements must not exceed 5000 characters")
        String requirements,

        @DecimalMin(value = "0.0", message = "proposedPrice must not be negative")
        BigDecimal proposedPrice,

        @Future(message = "scheduledAt must be in the future")
        LocalDateTime scheduledAt
) {}
