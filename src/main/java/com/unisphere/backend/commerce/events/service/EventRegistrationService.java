package com.unisphere.backend.commerce.events.service;

import com.unisphere.backend.commerce.events.dto.request.EventCheckInRequest;
import com.unisphere.backend.commerce.events.dto.request.EventRegistrationStatusUpdateRequest;
import com.unisphere.backend.commerce.events.dto.response.EventOrganizerResponse;
import com.unisphere.backend.commerce.events.dto.response.EventRegistrationResponse;
import com.unisphere.backend.commerce.events.entity.Event;
import com.unisphere.backend.commerce.events.entity.EventRegistration;
import com.unisphere.backend.commerce.events.enums.EventRegistrationMode;
import com.unisphere.backend.commerce.events.enums.EventRegistrationStatus;
import com.unisphere.backend.commerce.events.enums.EventStatus;
import com.unisphere.backend.commerce.events.repository.EventRegistrationRepository;
import com.unisphere.backend.commerce.events.repository.EventRepository;
import com.unisphere.backend.common.exception.EventNotFoundException;
import com.unisphere.backend.common.exception.EventRegistrationNotFoundException;
import com.unisphere.backend.common.exception.InvalidEventRegistrationTransitionException;
import com.unisphere.backend.common.storage.MediaUrlResolver;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.repository.UserRepository;
import com.unisphere.backend.identity.service.UserService;
import com.unisphere.backend.social.notification.enums.NotificationType;
import com.unisphere.backend.social.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Registration lifecycle: register (with capacity/waitlist decision), cancel (with FIFO waitlist
 * promotion), check-in, and every read over a registration.
 *
 * <p>{@code register}/{@code cancel} run entirely inside a transaction that holds
 * {@link EventRepository#findByIdForUpdate} on the event row — see that method's javadoc for why a
 * plain {@code COUNT()} check would let two concurrent requests both seat the last open spot.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class EventRegistrationService {

    private final EventRepository eventRepository;
    private final EventRegistrationRepository eventRegistrationRepository;
    private final EventService eventService;
    private final EventAccessService accessService;
    private final EventRealtimeService realtimeService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final MediaUrlResolver mediaUrlResolver;

    public EventRegistrationResponse register(Long eventId, User currentUser) {
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new IllegalArgumentException("Registration is only open for published events");
        }
        if (event.getRegistrationMode() != EventRegistrationMode.INTERNAL) {
            throw new IllegalArgumentException(
                    "This event uses external registration: " + event.getExternalRegistrationUrl());
        }
        if (!event.getStartDatetime().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Registration is closed — this event has already started");
        }
        // The organizer is implicitly "attending" their own event and does not occupy a
        // capacity-limited seat slot as a registrant.
        if (event.getOrganizerId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("You cannot register for an event you organize");
        }
        // ATTENDED must be included alongside REGISTERED/WAITLISTED: without it, a user already
        // checked in (e.g. the organizer let attendees in before startDatetime formally passed)
        // could register a second time and be issued a second ticket for the same event.
        if (eventRegistrationRepository.existsByEventIdAndUserIdAndStatusIn(eventId, currentUser.getId(),
                List.of(EventRegistrationStatus.REGISTERED, EventRegistrationStatus.WAITLISTED,
                        EventRegistrationStatus.ATTENDED))) {
            throw new IllegalArgumentException("You are already registered for this event");
        }

        EventRegistrationStatus status;
        if (event.getMaxCapacity() == null || event.getRegisteredCount() < event.getMaxCapacity()) {
            status = EventRegistrationStatus.REGISTERED;
            event.setRegisteredCount(event.getRegisteredCount() + 1);
        } else {
            status = EventRegistrationStatus.WAITLISTED;
            event.setWaitlistedCount(event.getWaitlistedCount() + 1);
        }

        EventRegistration registration = new EventRegistration();
        registration.setEventId(eventId);
        registration.setUserId(currentUser.getId());
        registration.setStatus(status);
        registration.setTicketCode(UUID.randomUUID().toString());

        EventRegistration saved = eventRegistrationRepository.save(registration);
        eventRepository.save(event);
        realtimeService.publishSeatUpdate(event);

        // actorId = null: a system notification about the outcome of the caller's own action, not a
        // "someone did X to you" social event. createAndPush no-ops when userId.equals(actorId),
        // which would incorrectly swallow this confirmation if actor were set to the caller.
        notificationService.createAndPush(currentUser.getId(), null, NotificationType.EVENT, eventId,
                status == EventRegistrationStatus.REGISTERED
                        ? "EVENT_REGISTRATION_CONFIRMED" : "EVENT_REGISTRATION_WAITLISTED");

        // currentUser is already loaded — the registrant is always the caller here, so no extra query.
        return toResponse(saved, event.getTitle(), resolveAttendee(currentUser));
    }

    /**
     * The client-facing entry point for {@code PATCH /events/registrations/{id}}. Only
     * {@code CANCELLED} is ever a valid client-supplied value — {@code ATTENDED} is reachable only
     * through {@link #checkIn}, and {@code REGISTERED} only through system waitlist promotion.
     */
    public EventRegistrationResponse updateRegistrationStatus(Long registrationId,
                                                               EventRegistrationStatusUpdateRequest req,
                                                               User currentUser) {
        if (req.status() != EventRegistrationStatus.CANCELLED) {
            throw new InvalidEventRegistrationTransitionException(
                    "Only CANCELLED is accepted here — ATTENDED is set via check-in, "
                            + "REGISTERED via automatic waitlist promotion");
        }
        return cancel(registrationId, req.reason(), currentUser);
    }

    private EventRegistrationResponse cancel(Long registrationId, String reason, User currentUser) {
        EventRegistration registration = findRegistration(registrationId);
        Event event = eventRepository.findByIdForUpdate(registration.getEventId())
                .orElseThrow(() -> new EventNotFoundException(registration.getEventId()));
        accessService.assertCanManageRegistration(registration, event, currentUser);

        EventRegistrationStatus from = registration.getStatus();
        if (from != EventRegistrationStatus.REGISTERED && from != EventRegistrationStatus.WAITLISTED) {
            throw new InvalidEventRegistrationTransitionException(from, EventRegistrationStatus.CANCELLED);
        }

        registration.setStatus(EventRegistrationStatus.CANCELLED);
        registration.setCancelledBy(currentUser.getId());
        registration.setCancellationReason(reason);
        registration.setCancelledAt(LocalDateTime.now());
        eventRegistrationRepository.save(registration);

        if (from == EventRegistrationStatus.REGISTERED) {
            event.setRegisteredCount(Math.max(event.getRegisteredCount() - 1, 0));
            eventRepository.save(event);
            promoteFromWaitlist(event);
        } else {
            event.setWaitlistedCount(Math.max(event.getWaitlistedCount() - 1, 0));
            eventRepository.save(event);
        }

        // Only notify when someone other than the registrant made the change — an organizer/admin
        // removal is news to the person removed; a self-cancel is not news to the person who did it.
        if (!registration.getUserId().equals(currentUser.getId())) {
            notificationService.createAndPush(registration.getUserId(), currentUser.getId(),
                    NotificationType.EVENT, event.getId(), "EVENT_REGISTRATION_REMOVED");
        }

        realtimeService.publishSeatUpdate(event);
        // The canceller and the registrant are frequently different people (an organizer/admin
        // removal) — always resolve the registrant's own info, never currentUser's.
        return toResponse(registration, event.getTitle(), resolveAttendee(registration.getUserId()));
    }

    /**
     * FIFO — first in line wins, no scoring or preference logic. Called only from inside
     * {@link #cancel}'s already-{@code findByIdForUpdate}-locked transaction, so the capacity read
     * here is safe from the same race the registration path guards against.
     */
    private void promoteFromWaitlist(Event event) {
        if (event.getMaxCapacity() != null && event.getRegisteredCount() >= event.getMaxCapacity()) {
            return;
        }
        eventRegistrationRepository
                .findFirstByEventIdAndStatusOrderByCreatedAtAsc(event.getId(), EventRegistrationStatus.WAITLISTED)
                .ifPresent(promoted -> {
                    promoted.setStatus(EventRegistrationStatus.REGISTERED);
                    eventRegistrationRepository.save(promoted);
                    event.setRegisteredCount(event.getRegisteredCount() + 1);
                    event.setWaitlistedCount(Math.max(event.getWaitlistedCount() - 1, 0));
                    eventRepository.save(event);
                    notificationService.createAndPush(promoted.getUserId(), null, NotificationType.EVENT,
                            event.getId(), "EVENT_WAITLIST_PROMOTED");
                });
    }

    /**
     * Organizer/ADMIN scans a ticket at the door. No capacity lock needed — check-in only changes
     * the individual row's status, never {@code registeredCount}/{@code waitlistedCount}.
     */
    public EventRegistrationResponse checkIn(Long eventId, EventCheckInRequest req, User currentUser) {
        Event event = eventService.findViewableEvent(eventId, currentUser);
        accessService.assertCanCheckIn(event, currentUser);

        EventRegistration registration = eventRegistrationRepository
                .findByEventIdAndTicketCode(eventId, req.ticketCode())
                .orElseThrow(() -> new IllegalArgumentException("Invalid ticket code for this event"));

        switch (registration.getStatus()) {
            case CANCELLED -> throw new InvalidEventRegistrationTransitionException(
                    "This registration was cancelled");
            case ATTENDED -> throw new InvalidEventRegistrationTransitionException(
                    "Already checked in at " + registration.getCheckedInAt());
            case WAITLISTED -> throw new InvalidEventRegistrationTransitionException(
                    "This ticket is on the waitlist, not a confirmed seat");
            case REGISTERED -> { /* proceed */ }
        }

        registration.setStatus(EventRegistrationStatus.ATTENDED);
        registration.setCheckedInAt(LocalDateTime.now());
        registration.setCheckedInBy(currentUser.getId());
        EventRegistration saved = eventRegistrationRepository.save(registration);

        // currentUser here is the door-staff organizer/admin scanning the ticket, not the attendee.
        return toResponse(saved, event.getTitle(), resolveAttendee(saved.getUserId()));
    }

    // ── Reads ────────────────────────────────────────────────────────────────

    /** Attendee list / check-in roster / export. Organizer/ADMIN only — not a public roster. */
    @Transactional(readOnly = true)
    public Page<EventRegistrationResponse> listRegistrations(Long eventId, EventRegistrationStatus status,
                                                              Pageable pageable, User currentUser) {
        Event event = eventService.findViewableEvent(eventId, currentUser);
        accessService.assertCanModify(event, currentUser);
        Page<EventRegistration> page = eventRegistrationRepository.findByEvent(eventId, status, pageable);
        Map<Long, User> attendees = loadAttendees(page.getContent());
        return page.map(r -> toResponse(r, event.getTitle(), resolveAttendee(r.getUserId(), attendees)));
    }

    @Transactional(readOnly = true)
    public EventRegistrationResponse myRegistrationForEvent(Long eventId, User currentUser) {
        Event event = eventService.findViewableEvent(eventId, currentUser);
        EventRegistration registration = eventRegistrationRepository
                .findFirstByEventIdAndUserIdOrderByCreatedAtDesc(eventId, currentUser.getId())
                .orElseThrow(() -> new EventRegistrationNotFoundException(eventId));
        return toResponse(registration, event.getTitle(), resolveAttendee(currentUser));
    }

    /** "My tickets" across every event — always the caller's own, so the attendee block is one lookup. */
    @Transactional(readOnly = true)
    public Page<EventRegistrationResponse> myRegistrations(EventRegistrationStatus status, Pageable pageable,
                                                            User currentUser) {
        Page<EventRegistration> page = eventRegistrationRepository.findByUser(currentUser.getId(), status, pageable);
        Map<Long, String> eventTitles = loadEventTitles(page.getContent());
        EventOrganizerResponse attendee = resolveAttendee(currentUser);
        return page.map(r -> toResponse(r, eventTitles.get(r.getEventId()), attendee));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Map<Long, String> loadEventTitles(List<EventRegistration> registrations) {
        if (registrations.isEmpty()) return Map.of();
        Set<Long> eventIds = registrations.stream().map(EventRegistration::getEventId).collect(Collectors.toSet());
        return eventRepository.findAllById(eventIds).stream()
                .collect(Collectors.toMap(Event::getId, Event::getTitle));
    }

    /** Batch-loads every distinct registrant on a page of registrations — one query, not one per row. */
    private Map<Long, User> loadAttendees(List<EventRegistration> registrations) {
        if (registrations.isEmpty()) return Map.of();
        Set<Long> userIds = registrations.stream().map(EventRegistration::getUserId).collect(Collectors.toSet());
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private EventOrganizerResponse resolveAttendee(User user) {
        return new EventOrganizerResponse(user.getId(), UserService.resolveDisplayName(user),
                mediaUrlResolver.toViewableUrl(user.getAvatarUrl()), user.getRole().name());
    }

    /** Batch-map lookup — for a page of registrations, paired with {@link #loadAttendees}. */
    private EventOrganizerResponse resolveAttendee(Long userId, Map<Long, User> users) {
        User user = users.get(userId);
        return user != null ? resolveAttendee(user) : unknownAttendee(userId);
    }

    /** Single-row lookup — for the write paths (cancel/check-in) where the actor isn't the registrant. */
    private EventOrganizerResponse resolveAttendee(Long userId) {
        return userRepository.findById(userId).map(this::resolveAttendee).orElseGet(() -> unknownAttendee(userId));
    }

    private EventOrganizerResponse unknownAttendee(Long userId) {
        return new EventOrganizerResponse(userId, "Unknown", null, "UNKNOWN");
    }

    private EventRegistration findRegistration(Long registrationId) {
        return eventRegistrationRepository.findById(registrationId)
                .orElseThrow(() -> new EventRegistrationNotFoundException(registrationId));
    }

    private EventRegistrationResponse toResponse(EventRegistration registration, String eventTitle,
                                                  EventOrganizerResponse attendee) {
        return new EventRegistrationResponse(
                registration.getId(), registration.getEventId(), eventTitle, registration.getUserId(), attendee,
                registration.getStatus(), registration.getTicketCode(), registration.getCheckedInAt(),
                registration.getCancelledBy(), registration.getCancellationReason(), registration.getCancelledAt(),
                registration.getCreatedAt());
    }
}
