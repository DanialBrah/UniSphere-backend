package com.unisphere.backend.commerce.events.service;

import com.unisphere.backend.commerce.events.dto.request.CreateEventRequest;
import com.unisphere.backend.commerce.events.dto.request.EventStatusUpdateRequest;
import com.unisphere.backend.commerce.events.dto.request.UpdateEventRequest;
import com.unisphere.backend.commerce.events.dto.response.EventMapPinResponse;
import com.unisphere.backend.commerce.events.dto.response.EventNearbyResponse;
import com.unisphere.backend.commerce.events.dto.response.EventOrganizerResponse;
import com.unisphere.backend.commerce.events.dto.response.EventResponse;
import com.unisphere.backend.commerce.events.dto.response.EventStatsResponse;
import com.unisphere.backend.commerce.events.dto.response.EventSummaryResponse;
import com.unisphere.backend.commerce.events.entity.Event;
import com.unisphere.backend.commerce.events.entity.EventRegistration;
import com.unisphere.backend.commerce.events.enums.EventCategory;
import com.unisphere.backend.commerce.events.enums.EventRegistrationMode;
import com.unisphere.backend.commerce.events.enums.EventRegistrationStatus;
import com.unisphere.backend.commerce.events.enums.EventStatus;
import com.unisphere.backend.commerce.events.repository.EventRegistrationRepository;
import com.unisphere.backend.commerce.events.repository.EventRepository;
import com.unisphere.backend.common.exception.EventNotFoundException;
import com.unisphere.backend.common.exception.InvalidEventStatusTransitionException;
import com.unisphere.backend.common.storage.MediaUrlResolver;
import com.unisphere.backend.identity.entity.Role;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.repository.UserRepository;
import com.unisphere.backend.identity.service.UserService;
import com.unisphere.backend.social.notification.enums.NotificationType;
import com.unisphere.backend.social.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Event CRUD, the browse feed, search, the two geo queries, per-event stats, and every response
 * mapping in the module. The three private {@code to*} methods at the bottom are the only places an
 * {@link Event} is turned into a DTO — unlike {@code LostFoundService} there is no privacy mask to
 * enforce here (event locations are meant to be public), but centralising the mapping still keeps
 * the organizer/viewer-registration-status batching in one place.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class EventService {

    /** FULLTEXT BOOLEAN MODE treats these as operators; an unbalanced one is a MySQL syntax error. */
    private static final Pattern FULLTEXT_OPERATORS = Pattern.compile("[+\\-><()~*\"@]");

    /** Kilometres per degree of latitude. Longitude shrinks by cos(latitude); see bbox maths below. */
    private static final double KM_PER_DEGREE = 111.045;

    private static final double EARTH_RADIUS_KM = 6371.0;

    private final EventRepository eventRepository;
    private final EventRegistrationRepository eventRegistrationRepository;
    private final UserRepository userRepository;
    private final MediaUrlResolver mediaUrlResolver;
    private final EventAccessService accessService;
    private final EventMediaService eventMediaService;
    private final NotificationService notificationService;

    @Value("${events.nearby-default-radius-km:5}")
    private double defaultRadiusKm;

    @Value("${events.nearby-max-radius-km:50}")
    private double maxRadiusKm;

    @Value("${events.map-max-pins:500}")
    private int mapMaxPins;

    // ── Writes ───────────────────────────────────────────────────────────────

    public EventResponse createEvent(CreateEventRequest req, User currentUser) {
        validateDateRange(req.startDatetime(), req.endDatetime());
        validateLocationRules(req.online(), req.onlineUrl(), req.venueName(), req.latitude(), req.longitude());
        validateRegistrationRules(req.registrationMode(), req.externalRegistrationUrl(), req.maxCapacity());

        Event event = new Event();
        event.setOrganizerId(currentUser.getId());
        // Derived from the organizer, never from the request — see EventAccessService.
        event.setUniversityId(accessService.resolveEventUniversityId(currentUser));
        event.setTitle(req.title());
        event.setDescription(req.description());
        event.setCategory(req.category() != null ? req.category() : EventCategory.OTHER);
        // Every event is born DRAFT — publishing is an explicit later action.
        event.setStatus(EventStatus.DRAFT);
        event.setStartDatetime(req.startDatetime());
        event.setEndDatetime(req.endDatetime());
        event.setOnline(req.online());
        event.setOnlineUrl(req.onlineUrl());
        event.setVenueName(req.venueName());
        event.setLatitude(req.latitude());
        event.setLongitude(req.longitude());
        event.setRegistrationMode(req.registrationMode());
        event.setExternalRegistrationUrl(req.externalRegistrationUrl());
        event.setMaxCapacity(req.maxCapacity());

        if (req.coverImageKey() != null && !req.coverImageKey().isBlank()) {
            eventMediaService.assertOwnedKey(req.coverImageKey(), currentUser);
            event.setCoverImageKey(req.coverImageKey());
        }

        return toResponse(eventRepository.save(event), currentUser);
    }

    public EventResponse updateEvent(Long eventId, UpdateEventRequest req, User currentUser) {
        Event event = findViewableEvent(eventId, currentUser);
        accessService.assertCanModify(event, currentUser);

        if (req.category() != null) event.setCategory(req.category());
        if (req.title() != null) {
            if (req.title().isBlank()) {
                throw new IllegalArgumentException("title cannot be blank");
            }
            event.setTitle(req.title());
        }
        if (req.description() != null) event.setDescription(blankToNull(req.description()));
        if (req.startDatetime() != null) event.setStartDatetime(req.startDatetime());
        if (req.endDatetime() != null) event.setEndDatetime(req.endDatetime());

        if (req.coverImageKey() != null) {
            if (req.coverImageKey().isBlank()) {
                event.setCoverImageKey(null);
            } else {
                eventMediaService.assertOwnedKey(req.coverImageKey(), currentUser);
                event.setCoverImageKey(req.coverImageKey());
            }
        }

        if (req.online() != null) event.setOnline(req.online());
        if (req.onlineUrl() != null) event.setOnlineUrl(blankToNull(req.onlineUrl()));
        applyLocation(event, req);

        if (req.registrationMode() != null) event.setRegistrationMode(req.registrationMode());
        if (req.externalRegistrationUrl() != null) {
            event.setExternalRegistrationUrl(blankToNull(req.externalRegistrationUrl()));
        }
        applyMaxCapacity(event, req);

        // Re-check every cross-field invariant against the full post-update state, not just the
        // fields that changed this call — mirrors LostFoundService.updateItem re-validating the
        // FOUND-pickup rule after every field change.
        validateDateRange(event.getStartDatetime(), event.getEndDatetime());
        validateLocationRules(event.isOnline(), event.getOnlineUrl(), event.getVenueName(),
                event.getLatitude(), event.getLongitude());
        validateRegistrationRules(event.getRegistrationMode(), event.getExternalRegistrationUrl(),
                event.getMaxCapacity());

        return toResponse(eventRepository.save(event), currentUser);
    }

    /**
     * The event lifecycle, in one place. {@code COMPLETED} is rejected outright: it is written only
     * by {@code EventCompletionScheduler}, since completion is a policy outcome ("end_datetime has
     * passed"), not a user intent.
     */
    public EventResponse changeStatus(Long eventId, EventStatusUpdateRequest req, User currentUser) {
        Event event = findViewableEvent(eventId, currentUser);
        accessService.assertCanModify(event, currentUser);

        EventStatus from = event.getStatus();
        EventStatus to = req.status();

        if (to == EventStatus.COMPLETED) {
            throw new InvalidEventStatusTransitionException(
                    "COMPLETED is set by the automatic completion sweep, not directly");
        }
        if (from == to) {
            throw new InvalidEventStatusTransitionException(from, to);
        }

        boolean allowed = switch (from) {
            case DRAFT -> to == EventStatus.PUBLISHED || to == EventStatus.CANCELLED;
            case PUBLISHED -> to == EventStatus.CANCELLED;
            case CANCELLED, COMPLETED -> false;
        };
        if (!allowed) {
            throw new InvalidEventStatusTransitionException(from, to);
        }

        event.setStatus(to);
        Event saved = eventRepository.save(event);

        if (to == EventStatus.CANCELLED) {
            notifyRegistrantsOfCancellation(saved, currentUser);
        }

        return toResponse(saved, currentUser);
    }

    /**
     * Soft delete. Blocked while the event still has active registrations — deleting it out from
     * under people who registered is destructive in a way {@code CANCELLED} (which notifies
     * everyone) is not, so the caller is told to cancel instead.
     */
    public void deleteEvent(Long eventId, User currentUser) {
        Event event = findViewableEvent(eventId, currentUser);
        accessService.assertCanModify(event, currentUser);
        if (event.getRegisteredCount() > 0 || event.getWaitlistedCount() > 0) {
            throw new IllegalArgumentException(
                    "This event still has active registrations — cancel it instead of deleting it");
        }
        event.setDeletedAt(LocalDateTime.now());
        eventRepository.save(event);
    }

    // ── Reads ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public EventResponse getEventById(Long eventId, User currentUser) {
        return toResponse(findViewableEvent(eventId, currentUser), currentUser);
    }

    /**
     * The browse feed. A client-supplied {@code DRAFT} filter is rejected outright — drafts are only
     * visible via {@code /events/me} — and a null filter defaults to {@code PUBLISHED}. Any other
     * explicit status (notably {@code COMPLETED}) passes through unchanged, which is what lets a
     * client reach a "past events" view through this same endpoint without a separate
     * upcoming/past parameter: {@code EventCompletionScheduler} is what turns "still PUBLISHED"
     * into "already COMPLETED" over time.
     */
    @Transactional(readOnly = true)
    public Page<EventSummaryResponse> getFeed(EventCategory category,
                                              EventStatus status,
                                              Boolean isOnline,
                                              Long organizerId,
                                              Pageable pageable,
                                              User currentUser) {
        if (status == EventStatus.DRAFT) {
            throw new IllegalArgumentException("Drafts are only visible via /events/me");
        }
        EventStatus effectiveStatus = status != null ? status : EventStatus.PUBLISHED;
        return toSummaryResponses(
                eventRepository.findFeed(isAdmin(currentUser), accessService.viewerUniversityId(currentUser),
                        effectiveStatus, category, isOnline, organizerId, pageable),
                currentUser);
    }

    @Transactional(readOnly = true)
    public Page<EventSummaryResponse> search(String query, EventCategory category, Pageable pageable, User currentUser) {
        String sanitized = sanitizeBooleanQuery(query);
        if (sanitized.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        return toSummaryResponses(
                eventRepository.searchFullText(sanitized, isAdmin(currentUser),
                        accessService.viewerUniversityId(currentUser), nameOf(category), stripSort(pageable)),
                currentUser);
    }

    @Transactional(readOnly = true)
    public Page<EventSummaryResponse> getMyEvents(EventStatus status, Pageable pageable, User currentUser) {
        return toSummaryResponses(
                eventRepository.findByOrganizerId(currentUser.getId(), status, pageable), currentUser);
    }

    /**
     * Radius search. The bounding box is computed here, in Java, rather than in SQL — a per-row
     * expression on the latitude column would defeat {@code idx_event_geo} entirely.
     */
    @Transactional(readOnly = true)
    public Page<EventNearbyResponse> getNearby(BigDecimal lat, BigDecimal lng, Double radiusKm,
                                               EventCategory category, Pageable pageable, User currentUser) {
        requireCoordinate(lat, -90, 90, "lat");
        requireCoordinate(lng, -180, 180, "lng");

        double radius = radiusKm != null ? radiusKm : defaultRadiusKm;
        radius = Math.min(Math.max(radius, 0.1), maxRadiusKm);

        double latDelta = radius / KM_PER_DEGREE;
        double lngDelta = radius / (KM_PER_DEGREE * Math.max(Math.cos(Math.toRadians(lat.doubleValue())), 0.01));

        Page<Event> page = eventRepository.findNearby(
                lat, lng,
                lat.subtract(BigDecimal.valueOf(latDelta)), lat.add(BigDecimal.valueOf(latDelta)),
                lng.subtract(BigDecimal.valueOf(lngDelta)), lng.add(BigDecimal.valueOf(lngDelta)),
                radius,
                isAdmin(currentUser), accessService.viewerUniversityId(currentUser),
                nameOf(category),
                stripSort(pageable));

        PageContext ctx = loadPageContext(page.getContent(), currentUser);
        return page.map(event -> new EventNearbyResponse(
                toSummaryResponse(event, ctx),
                haversineKm(lat, lng, event.getLatitude(), event.getLongitude())));
    }

    /**
     * Map viewport. Returns a hard-capped list rather than a Page — a map client re-queries on pan
     * instead of paginating, and a count query would be pure waste.
     */
    @Transactional(readOnly = true)
    public List<EventMapPinResponse> getMapPins(BigDecimal minLat, BigDecimal maxLat,
                                                BigDecimal minLng, BigDecimal maxLng,
                                                EventCategory category, User currentUser) {
        requireCoordinate(minLat, -90, 90, "minLat");
        requireCoordinate(maxLat, -90, 90, "maxLat");
        requireCoordinate(minLng, -180, 180, "minLng");
        requireCoordinate(maxLng, -180, 180, "maxLng");
        if (minLat.compareTo(maxLat) >= 0) {
            throw new IllegalArgumentException("minLat must be less than maxLat");
        }
        if (minLng.compareTo(maxLng) >= 0) {
            throw new IllegalArgumentException(
                    "minLng must be less than maxLng (viewports crossing the antimeridian are not supported)");
        }

        List<EventRepository.EventMapPinView> pins = eventRepository.findInViewport(
                minLat, maxLat, minLng, maxLng,
                isAdmin(currentUser), accessService.viewerUniversityId(currentUser),
                category, PageRequest.of(0, mapMaxPins));
        return pins.stream().map(this::toPin).toList();
    }

    @Transactional(readOnly = true)
    public EventStatsResponse getStats(Long eventId, User currentUser) {
        Event event = findActiveEvent(eventId);
        accessService.assertCanModify(event, currentUser);

        Map<EventRegistrationStatus, Long> byStatus = zeroFilled(EventRegistrationStatus.class);
        eventRegistrationRepository.countByEventIdGroupByStatus(eventId)
                .forEach(row -> byStatus.put(row.getStatus(), row.getTotal()));

        Integer available = event.getMaxCapacity() == null
                ? null : Math.max(event.getMaxCapacity() - event.getRegisteredCount(), 0);

        return new EventStatsResponse(
                byStatus.get(EventRegistrationStatus.REGISTERED),
                byStatus.get(EventRegistrationStatus.WAITLISTED),
                byStatus.get(EventRegistrationStatus.ATTENDED),
                byStatus.get(EventRegistrationStatus.CANCELLED),
                event.getMaxCapacity(), available);
    }

    // ── Shared with EventRegistrationService ────────────────────────────────

    Event findActiveEvent(Long eventId) {
        return eventRepository.findActiveById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    /**
     * Masks a DRAFT event as 404 for anyone but its organizer/ADMIN, so the row's existence never
     * leaks through a 403 — the same nuance {@code NewsService.findViewableArticle} applies to
     * scheduled/unpublished articles. Every read path and every write path (before
     * {@code assertCanModify}) routes through this rather than {@link #findActiveEvent}.
     */
    Event findViewableEvent(Long eventId, User viewer) {
        Event event = findActiveEvent(eventId);
        if (event.getStatus() == EventStatus.DRAFT && !accessService.isOwnerOrAdmin(event, viewer)) {
            throw new EventNotFoundException(eventId);
        }
        return event;
    }

    EventResponse toResponse(Event event, User currentUser) {
        PageContext ctx = loadPageContext(List.of(event), currentUser);
        return buildResponse(event, ctx);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean isAdmin(User user) {
        return user.getRole() == Role.ADMIN;
    }

    private Pageable stripSort(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    private String nameOf(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String sanitizeBooleanQuery(String raw) {
        if (raw == null) return "";
        return FULLTEXT_OPERATORS.matcher(raw).replaceAll(" ").trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private <E extends Enum<E>> Map<E, Long> zeroFilled(Class<E> type) {
        Map<E, Long> map = new EnumMap<>(type);
        for (E constant : type.getEnumConstants()) map.put(constant, 0L);
        return map;
    }

    private void requireCoordinate(BigDecimal value, double min, double max, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (value.doubleValue() < min || value.doubleValue() > max) {
            throw new IllegalArgumentException(field + " must be between " + min + " and " + max);
        }
    }

    private void validateDateRange(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null && !end.isAfter(start)) {
            throw new IllegalArgumentException("endDatetime must be after startDatetime");
        }
    }

    /** A physical event with no pin defeats the map feature; an online event with a pin is just noise. */
    private void validateLocationRules(boolean online, String onlineUrl, String venueName,
                                       BigDecimal latitude, BigDecimal longitude) {
        if (online) {
            if (onlineUrl == null || onlineUrl.isBlank()) {
                throw new IllegalArgumentException("An online event requires onlineUrl");
            }
            if (venueName != null || latitude != null || longitude != null) {
                throw new IllegalArgumentException("An online event cannot have a venueName or coordinates");
            }
        } else {
            if (venueName == null || venueName.isBlank() || latitude == null || longitude == null) {
                throw new IllegalArgumentException("A physical event requires venueName and coordinates");
            }
            if (onlineUrl != null) {
                throw new IllegalArgumentException("A physical event cannot have an onlineUrl");
            }
        }
    }

    /** Capacity is meaningless without in-app registration tracking, so EXTERNAL forbids it. */
    private void validateRegistrationRules(EventRegistrationMode mode, String externalRegistrationUrl,
                                           Integer maxCapacity) {
        if (mode == EventRegistrationMode.EXTERNAL) {
            if (externalRegistrationUrl == null || externalRegistrationUrl.isBlank()) {
                throw new IllegalArgumentException("External registration requires externalRegistrationUrl");
            }
            if (maxCapacity != null) {
                throw new IllegalArgumentException("maxCapacity is not supported for external registration");
            }
        } else if (externalRegistrationUrl != null) {
            throw new IllegalArgumentException(
                    "externalRegistrationUrl is only valid when registrationMode is EXTERNAL");
        }
    }

    private void applyLocation(Event event, UpdateEventRequest req) {
        if (req.venueName() != null || req.latitude() != null || req.longitude() != null) {
            if (req.venueName() != null) event.setVenueName(blankToNull(req.venueName()));
            if (req.latitude() != null) event.setLatitude(req.latitude());
            if (req.longitude() != null) event.setLongitude(req.longitude());
        } else if (Boolean.TRUE.equals(req.clearLocation())) {
            event.setVenueName(null);
            event.setLatitude(null);
            event.setLongitude(null);
        }
    }

    private void applyMaxCapacity(Event event, UpdateEventRequest req) {
        if (req.maxCapacity() != null) {
            event.setMaxCapacity(req.maxCapacity());
        } else if (Boolean.TRUE.equals(req.clearMaxCapacity())) {
            event.setMaxCapacity(null);
        }
    }

    private void notifyRegistrantsOfCancellation(Event event, User organizer) {
        List<Long> registered = eventRegistrationRepository
                .findUserIdsByEventIdAndStatus(event.getId(), EventRegistrationStatus.REGISTERED);
        List<Long> waitlisted = eventRegistrationRepository
                .findUserIdsByEventIdAndStatus(event.getId(), EventRegistrationStatus.WAITLISTED);
        Stream.concat(registered.stream(), waitlisted.stream()).forEach(userId ->
                notificationService.createAndPush(userId, organizer.getId(),
                        NotificationType.EVENT, event.getId(), "EVENT_CANCELLED"));
    }

    /** Great-circle distance in km. Null-safe: events without coordinates report 0. */
    private double haversineKm(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) return 0d;
        double dLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLng = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1.doubleValue())) * Math.cos(Math.toRadians(lat2.doubleValue()))
                * Math.pow(Math.sin(dLng / 2), 2);
        return EARTH_RADIUS_KM * 2 * Math.asin(Math.sqrt(a));
    }

    // ── Response mapping ─────────────────────────────────────────────────────

    private Page<EventSummaryResponse> toSummaryResponses(Page<Event> events, User currentUser) {
        PageContext ctx = loadPageContext(events.getContent(), currentUser);
        return events.map(e -> toSummaryResponse(e, ctx));
    }

    /**
     * Everything a page of events needs that isn't on the rows themselves, loaded in two queries
     * regardless of page size: the organizer block, and the viewer's own registration status per
     * event (batched, not looked up per row).
     */
    private record PageContext(User viewer, Map<Long, User> organizers,
                               Map<Long, EventRegistrationStatus> viewerRegistrationStatuses) {}

    private PageContext loadPageContext(List<Event> events, User currentUser) {
        if (events.isEmpty()) return new PageContext(currentUser, Map.of(), Map.of());

        Set<Long> eventIds = events.stream().map(Event::getId).collect(Collectors.toSet());
        Set<Long> organizerIds = events.stream().map(Event::getOrganizerId).collect(Collectors.toSet());

        // A user may hold more than one historical row per event (cancel, then re-register later —
        // there is deliberately no unique constraint, see the event_registrations migration note),
        // so the viewer's "current" status is the most recently created row, not just any row.
        Map<Long, Optional<EventRegistration>> latestByEvent = eventRegistrationRepository
                .findByEventIdInAndUserId(eventIds, currentUser.getId()).stream()
                .collect(Collectors.groupingBy(EventRegistration::getEventId,
                        Collectors.maxBy(Comparator.comparing(EventRegistration::getCreatedAt))));
        Map<Long, EventRegistrationStatus> viewerStatuses = latestByEvent.entrySet().stream()
                .filter(e -> e.getValue().isPresent())
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get().getStatus()));

        return new PageContext(
                currentUser,
                userRepository.findAllById(organizerIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u)),
                viewerStatuses);
    }

    private EventResponse buildResponse(Event event, PageContext ctx) {
        Integer available = event.getMaxCapacity() == null
                ? null : Math.max(event.getMaxCapacity() - event.getRegisteredCount(), 0);
        return new EventResponse(
                event.getId(),
                resolveOrganizer(event.getOrganizerId(), ctx.organizers()),
                event.getCategory(), event.getStatus(),
                event.getTitle(), event.getDescription(),
                mediaUrlResolver.toViewableUrl(event.getCoverImageKey()),
                event.getStartDatetime(), event.getEndDatetime(),
                event.isOnline(), event.getOnlineUrl(),
                event.getVenueName(), event.getLatitude(), event.getLongitude(),
                event.getRegistrationMode(), event.getExternalRegistrationUrl(),
                event.getMaxCapacity(), event.getRegisteredCount(), event.getWaitlistedCount(), available,
                event.getUniversityId(),
                ctx.viewerRegistrationStatuses().get(event.getId()),
                accessService.isOwnerOrAdmin(event, ctx.viewer()),
                event.getCreatedAt(), event.getUpdatedAt());
    }

    private EventSummaryResponse toSummaryResponse(Event event, PageContext ctx) {
        Integer available = event.getMaxCapacity() == null
                ? null : Math.max(event.getMaxCapacity() - event.getRegisteredCount(), 0);
        return new EventSummaryResponse(
                event.getId(),
                resolveOrganizer(event.getOrganizerId(), ctx.organizers()),
                event.getCategory(), event.getStatus(), event.getTitle(),
                mediaUrlResolver.toViewableUrl(event.getCoverImageKey()),
                event.getStartDatetime(), event.getEndDatetime(),
                event.isOnline(), event.getVenueName(), event.getLatitude(), event.getLongitude(),
                event.getRegistrationMode(), event.getExternalRegistrationUrl(),
                event.getMaxCapacity(), event.getRegisteredCount(), event.getWaitlistedCount(), available,
                event.getUniversityId(),
                ctx.viewerRegistrationStatuses().get(event.getId()),
                accessService.isOwnerOrAdmin(event, ctx.viewer()),
                event.getCreatedAt());
    }

    private EventMapPinResponse toPin(EventRepository.EventMapPinView pin) {
        return new EventMapPinResponse(
                pin.getId(), pin.getTitle(), pin.getCategory(), pin.getStatus(), pin.getStartDatetime(),
                pin.getLatitude(), pin.getLongitude(),
                mediaUrlResolver.toViewableUrl(pin.getCoverImageKey()),
                pin.getMaxCapacity(), pin.getRegisteredCount());
    }

    private EventOrganizerResponse resolveOrganizer(Long organizerId, Map<Long, User> organizers) {
        User organizer = organizers.get(organizerId);
        if (organizer == null) return new EventOrganizerResponse(organizerId, "Unknown", null, "UNKNOWN");
        return new EventOrganizerResponse(organizer.getId(), UserService.resolveDisplayName(organizer),
                mediaUrlResolver.toViewableUrl(organizer.getAvatarUrl()), organizer.getRole().name());
    }
}
