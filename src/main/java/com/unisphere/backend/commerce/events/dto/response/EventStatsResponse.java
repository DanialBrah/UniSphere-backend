package com.unisphere.backend.commerce.events.dto.response;

/** Organizer/ADMIN-only per-event breakdown — {@code GET /events/{id}/stats}. */
public record EventStatsResponse(
        long registeredCount,
        long waitlistedCount,
        long attendedCount,
        long cancelledCount,
        Integer maxCapacity,
        Integer availableSeats
) {}
