package com.unisphere.backend.commerce.events.enums;

/**
 * How attendees register for an event.
 *
 * <p>{@link #INTERNAL} events accept {@code POST /events/{id}/register} and track capacity,
 * waitlisting and check-in in-app. {@link #EXTERNAL} events point the client at
 * {@code externalRegistrationUrl} instead — the organizer manages registration on another platform,
 * so {@code maxCapacity} is meaningless and must be {@code null} (enforced in {@code EventService}).
 */
public enum EventRegistrationMode {
    INTERNAL,
    EXTERNAL
}
