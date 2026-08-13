package com.unisphere.backend.commerce.events.enums;

/**
 * Event lifecycle. The transition table lives in {@code EventService.changeStatus}, which owns the
 * whole machine. One transition is deliberately unreachable from {@code PATCH /events/{id}/status}:
 *
 * <ul>
 *   <li>{@code -> COMPLETED} is written only by {@code EventCompletionScheduler}. Completion is a
 *       policy outcome ("{@code end_datetime} has passed"), not a user intent — an organizer has no
 *       business marking an event complete before it even starts.</li>
 * </ul>
 */
public enum EventStatus {

    /** Being edited by its organizer. Not visible on the public feed, search, or map. */
    DRAFT,

    /** Live and accepting registrations (if {@code registrationMode == INTERNAL}). */
    PUBLISHED,

    /** Withdrawn by the organizer. Every active registrant is notified. Terminal. */
    CANCELLED,

    /** {@code end_datetime} has passed. Set only by the scheduled sweep. Terminal. */
    COMPLETED
}
