package com.unisphere.backend.commerce.services.dto.request;

import com.unisphere.backend.commerce.services.enums.ServiceOrderStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The single entry point for every actor in the {@code ServiceOrderStatus} state machine — client,
 * provider or admin all funnel through this one request shape, exactly like
 * {@code JobApplicationStatusUpdateRequest} funnels the employer decision and the applicant's own
 * withdrawal through one shape. See {@code ServiceOrderService.updateOrderStatus} for exactly who
 * may fire which transition.
 *
 * @param reason       optional note — a decline/cancel/dispute rationale. Always stored; surfaced
 *                     to the other party in the resulting notification.
 * @param agreedPrice  only meaningful on a {@code PENDING -> ACCEPTED} transition for a
 *                     {@code NEGOTIABLE} listing whose order has no price yet — lets the provider
 *                     fill in the agreed figure in the same request rather than requiring a
 *                     separate step.
 * @param scheduledAt  optional reschedule, settable on any transition.
 */
public record ServiceOrderStatusUpdateRequest(

        @NotNull(message = "status is required")
        ServiceOrderStatus status,

        @Size(max = 255, message = "reason must not exceed 255 characters")
        String reason,

        @DecimalMin(value = "0.0", message = "agreedPrice must not be negative")
        BigDecimal agreedPrice,

        @Future(message = "scheduledAt must be in the future")
        LocalDateTime scheduledAt
) {}
