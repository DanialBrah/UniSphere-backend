package com.unisphere.backend.commerce.events.dto.response;

/**
 * The live seat-count broadcast payload, pushed to {@code /topic/events/{eventId}/seats} by
 * {@code EventRealtimeService} after every registration, cancellation and waitlist promotion.
 *
 * @param availableSeats {@code maxCapacity - registeredCount}, or null when unlimited
 */
public record EventSeatsResponse(
        Long eventId,
        int registeredCount,
        Integer maxCapacity,
        Integer availableSeats,
        int waitlistedCount
) {}
