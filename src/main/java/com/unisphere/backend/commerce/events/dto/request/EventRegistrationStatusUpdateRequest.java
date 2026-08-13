package com.unisphere.backend.commerce.events.dto.request;

import com.unisphere.backend.commerce.events.enums.EventRegistrationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Only {@code CANCELLED} is ever a valid value here — {@code ATTENDED} is reachable only via
 * {@code POST /events/{id}/check-in} and {@code REGISTERED} only via system waitlist promotion.
 * Anything else throws {@code InvalidEventRegistrationTransitionException}.
 *
 * @param reason optional note, e.g. an organizer explaining why an attendee was removed. Always
 *               stored, but only surfaced in the removed-attendee notification when someone other
 *               than the registrant made the change — mirrors {@code LostFoundClaimDecisionRequest.decisionNote}.
 */
public record EventRegistrationStatusUpdateRequest(

        @NotNull(message = "status is required")
        EventRegistrationStatus status,

        @Size(max = 255, message = "reason must not exceed 255 characters")
        String reason
) {}
