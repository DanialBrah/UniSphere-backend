package com.unisphere.backend.commerce.events.dto.response;

/**
 * A wrapper rather than a nullable field on {@link EventSummaryResponse} — distance is only
 * meaningful on {@code GET /events/nearby} and would be null on every other endpoint. Mirrors
 * {@code LostFoundNearbyItemResponse}.
 */
public record EventNearbyResponse(
        EventSummaryResponse event,
        double distanceKm
) {}
