package com.unisphere.backend.commerce.events.dto.response;

import com.unisphere.backend.commerce.events.enums.EventRegistrationStatus;

import java.time.LocalDateTime;

/**
 * @param ticketCode   a bare scannable string — turning it into a QR image is a client-side concern
 * @param attendee     the registrant's display info — reuses {@link EventOrganizerResponse}'s shape
 *                     rather than a duplicate DTO, the same way {@code LostFoundUserResponse} backs
 *                     both a report's reporter and a claim's claimant
 * @param cancelledBy  who cancelled this registration, or null if it's still active. Compare against
 *                     {@code userId} to tell a self-cancel apart from an organizer/admin removal.
 * @param cancellationReason optional note left by whoever cancelled it
 */
public record EventRegistrationResponse(
        Long id,
        Long eventId,
        String eventTitle,
        Long userId,
        EventOrganizerResponse attendee,
        EventRegistrationStatus status,
        String ticketCode,
        LocalDateTime checkedInAt,
        Long cancelledBy,
        String cancellationReason,
        LocalDateTime cancelledAt,
        LocalDateTime createdAt
) {}
