package com.unisphere.backend.commerce.events.enums;

/**
 * Registration lifecycle. The transition table lives in {@code EventRegistrationService}. Two
 * transitions are deliberately unreachable from the client-facing {@code PATCH /events/registrations/{id}}:
 *
 * <ul>
 *   <li>{@code -> ATTENDED} is written only by {@code EventRegistrationService.checkIn}
 *       (organizer/ADMIN, via {@code POST /events/{id}/check-in}), never the generic status PATCH.</li>
 *   <li>{@code WAITLISTED -> REGISTERED} is written only by the system, on auto-promotion when an
 *       earlier {@link #REGISTERED} registrant cancels.</li>
 * </ul>
 */
public enum EventRegistrationStatus {

    /** Holds a confirmed seat. */
    REGISTERED,

    /** Capacity was full at registration time; promoted to {@link #REGISTERED} FIFO on a cancellation. */
    WAITLISTED,

    /** Withdrawn by the registrant, or removed by the organizer/ADMIN. Terminal. */
    CANCELLED,

    /** Checked in at the event by the organizer/ADMIN scanning {@code ticketCode}. Terminal. */
    ATTENDED
}
