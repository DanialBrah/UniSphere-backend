package com.unisphere.backend.commerce.events.repository;

import com.unisphere.backend.commerce.events.entity.Event;
import com.unisphere.backend.commerce.events.enums.EventCategory;
import com.unisphere.backend.commerce.events.enums.EventStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    // Explicit null check needed since @SQLRestriction filters soft-deleted rows,
    // but we need to fetch by ID for the delete/update path before evaluating it.
    @Query("SELECT e FROM Event e WHERE e.id = :id AND e.deletedAt IS NULL")
    Optional<Event> findActiveById(@Param("id") Long id);

    /**
     * Pessimistic row lock for the capacity check. Used only inside
     * {@code EventRegistrationService.register}/{@code cancel} — every write that touches
     * {@code registeredCount}/{@code waitlistedCount} takes this lock first, so two concurrent
     * registrations for the last open seat cannot both read "1 seat left" before either commits.
     * Reads (feed, map, detail) never take this lock; they read the denormalised counters directly.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);

    /**
     * The browse feed. {@code status} is always a concrete, resolved value by the time this is
     * called — {@code EventService.getFeed} defaults a null filter to PUBLISHED and rejects a
     * client-supplied DRAFT outright, so DRAFT can never reach this query.
     *
     * <p>isAdmin/viewerUniversityId are precomputed once per call in the service, so the visibility
     * predicate stays a plain column comparison and pagination totals remain exact.
     *
     * <p>No ORDER BY here on purpose: ordering comes from {@code @PageableDefault}/the caller's
     * explicit sort, so a query-embedded clause cannot silently end up merely a tiebreaker.
     */
    @Query("""
            SELECT e FROM Event e
            WHERE e.status = :status
              AND ( :isAdmin = true
                    OR e.universityId IS NULL
                    OR e.universityId = :viewerUniversityId )
              AND (:category    IS NULL OR e.category    = :category)
              AND (:isOnline    IS NULL OR e.online       = :isOnline)
              AND (:organizerId IS NULL OR e.organizerId  = :organizerId)
            """)
    Page<Event> findFeed(@Param("isAdmin") boolean isAdmin,
                         @Param("viewerUniversityId") Long viewerUniversityId,
                         @Param("status") EventStatus status,
                         @Param("category") EventCategory category,
                         @Param("isOnline") Boolean isOnline,
                         @Param("organizerId") Long organizerId,
                         Pageable pageable);

    /** The caller's own events, every status — no visibility predicate, they are all theirs. */
    @Query("""
            SELECT e FROM Event e
            WHERE e.organizerId = :organizerId
              AND (:status IS NULL OR e.status = :status)
            """)
    Page<Event> findByOrganizerId(@Param("organizerId") Long organizerId,
                                  @Param("status") EventStatus status,
                                  Pageable pageable);

    /**
     * Map viewport query. JPQL rather than native because the filter is pure column comparison with
     * no trigonometry, so {@code idx_event_geo} is used and {@code @SQLRestriction} applies
     * automatically.
     *
     * <p>Hardcoded to PUBLISHED only — a "what's happening near me" map has no use for drafts,
     * cancellations or events that already ended; a "past events" view is reached through the main
     * feed's {@code status=COMPLETED} filter instead, not the map.
     *
     * <p>Returns an interface projection, not entities: the service needs ten columns, not full
     * hydrated events, which is what makes a large viewport cheap. Unsorted, hard-capped
     * {@code PageRequest.of(0, cap)} passed by the caller — no count query, no Page, since a map
     * client re-queries on pan rather than paginating.
     */
    @Query("""
            SELECT e.id             AS id,
                   e.title          AS title,
                   e.category       AS category,
                   e.status         AS status,
                   e.startDatetime  AS startDatetime,
                   e.latitude       AS latitude,
                   e.longitude      AS longitude,
                   e.coverImageKey  AS coverImageKey,
                   e.maxCapacity    AS maxCapacity,
                   e.registeredCount AS registeredCount
            FROM Event e
            WHERE e.status = com.unisphere.backend.commerce.events.enums.EventStatus.PUBLISHED
              AND e.latitude  BETWEEN :minLat AND :maxLat
              AND e.longitude BETWEEN :minLng AND :maxLng
              AND ( :isAdmin = true
                    OR e.universityId IS NULL
                    OR e.universityId = :viewerUniversityId )
              AND (:category IS NULL OR e.category = :category)
            ORDER BY e.startDatetime ASC
            """)
    List<EventMapPinView> findInViewport(@Param("minLat") BigDecimal minLat,
                                         @Param("maxLat") BigDecimal maxLat,
                                         @Param("minLng") BigDecimal minLng,
                                         @Param("maxLng") BigDecimal maxLng,
                                         @Param("isAdmin") boolean isAdmin,
                                         @Param("viewerUniversityId") Long viewerUniversityId,
                                         @Param("category") EventCategory category,
                                         Pageable pageable);

    /** Projection for {@link #findInViewport} — the ten columns a map pin needs, nothing else. */
    interface EventMapPinView {
        Long getId();
        String getTitle();
        EventCategory getCategory();
        EventStatus getStatus();
        LocalDateTime getStartDatetime();
        BigDecimal getLatitude();
        BigDecimal getLongitude();
        String getCoverImageKey();
        Integer getMaxCapacity();
        int getRegisteredCount();
    }

    /**
     * Candidates for {@code EventReminderScheduler}: PUBLISHED, not yet reminded, starting within
     * the window. Returns entities (not a bulk update) because the scheduler needs each event's id
     * to fan out per-registrant notifications before flipping {@code reminderSentAt}.
     */
    @Query("""
            SELECT e FROM Event e
            WHERE e.status = com.unisphere.backend.commerce.events.enums.EventStatus.PUBLISHED
              AND e.reminderSentAt IS NULL
              AND e.startDatetime BETWEEN :now AND :windowEnd
            """)
    List<Event> findNeedingReminder(@Param("now") LocalDateTime now, @Param("windowEnd") LocalDateTime windowEnd);

    /**
     * Ages every ended PUBLISHED event to COMPLETED in one statement.
     *
     * <p>A bulk UPDATE rather than select-then-save: the WHERE clause carries {@code status =
     * PUBLISHED}, so a second Render replica running the same tick matches zero rows the second
     * time — safe with no distributed lock. {@code @SQLRestriction} is NOT applied to bulk HQL
     * updates, so {@code deletedAt IS NULL} is written by hand, and JPA auditing does not fire on
     * bulk updates either, so {@code updatedAt} is set explicitly. Mirrors
     * {@code LostFoundItemRepository.expireStaleItems} exactly.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Event e
               SET e.status    = com.unisphere.backend.commerce.events.enums.EventStatus.COMPLETED,
                   e.updatedAt = :now
             WHERE e.status    = com.unisphere.backend.commerce.events.enums.EventStatus.PUBLISHED
               AND e.endDatetime <= :now
               AND e.deletedAt IS NULL
            """)
    int completeEndedEvents(@Param("now") LocalDateTime now);

    // ── Native queries ──────────────────────────────────────────────────────────────────────────
    // Both share the same three obligations as LostFoundItemRepository's native queries:
    //   1. deleted_at IS NULL by hand — native SQL bypasses @SQLRestriction.
    //   2. status is a fixed 'PUBLISHED' literal, not a bound enum — same reasoning as
    //      findInViewport: search/nearby are discovery tools, not archival browsing.
    //   3. The caller strips any client-supplied sort — Spring appends the raw property name to
    //      native SQL, so ?sort=startDatetime emits ORDER BY startDatetime against a
    //      start_datetime column and 500s.

    /**
     * FULLTEXT search over title and description. The service sanitises the query first: an
     * unbalanced BOOLEAN-MODE operator is a MySQL syntax error that would surface as a 500 on a
     * plain user search.
     */
    @Query(value = """
            SELECT * FROM events e
            WHERE MATCH(e.title, e.description) AGAINST (:query IN BOOLEAN MODE)
              AND e.deleted_at IS NULL
              AND e.status = 'PUBLISHED'
              AND ( :isAdmin = true OR e.university_id IS NULL OR e.university_id = :viewerUniversityId )
              AND (:category IS NULL OR e.category = :category)
            ORDER BY e.start_datetime ASC, e.id ASC
            """,
           countQuery = """
            SELECT COUNT(*) FROM events e
            WHERE MATCH(e.title, e.description) AGAINST (:query IN BOOLEAN MODE)
              AND e.deleted_at IS NULL
              AND e.status = 'PUBLISHED'
              AND ( :isAdmin = true OR e.university_id IS NULL OR e.university_id = :viewerUniversityId )
              AND (:category IS NULL OR e.category = :category)
            """,
           nativeQuery = true)
    Page<Event> searchFullText(@Param("query") String query,
                               @Param("isAdmin") boolean isAdmin,
                               @Param("viewerUniversityId") Long viewerUniversityId,
                               @Param("category") String category,
                               Pageable pageable);

    /**
     * Radius search around a point, in two stages — same bbox-prefilter-then-Haversine-postfilter
     * shape as {@code LostFoundItemRepository.findNearby}. The bounding-box deltas are computed in
     * Java by the service, never in SQL, so nothing here is per-row and nothing defeats
     * {@code idx_event_geo}. 6371 is the mean Earth radius in km; {@code e.id ASC} is the tiebreaker
     * so two events at identical coordinates cannot swap places between pages.
     */
    @Query(value = """
            SELECT * FROM events e
            WHERE e.deleted_at IS NULL
              AND e.status = 'PUBLISHED'
              AND e.latitude  BETWEEN :minLat AND :maxLat
              AND e.longitude BETWEEN :minLng AND :maxLng
              AND ( :isAdmin = true OR e.university_id IS NULL OR e.university_id = :viewerUniversityId )
              AND (:category IS NULL OR e.category = :category)
              AND ( 6371 * 2 * ASIN(SQRT(
                      POWER(SIN(RADIANS(:lat - e.latitude) / 2), 2)
                    + COS(RADIANS(:lat)) * COS(RADIANS(e.latitude))
                    * POWER(SIN(RADIANS(:lng - e.longitude) / 2), 2)
                  )) ) <= :radiusKm
            ORDER BY ( 6371 * 2 * ASIN(SQRT(
                      POWER(SIN(RADIANS(:lat - e.latitude) / 2), 2)
                    + COS(RADIANS(:lat)) * COS(RADIANS(e.latitude))
                    * POWER(SIN(RADIANS(:lng - e.longitude) / 2), 2)
                  )) ) ASC, e.id ASC
            """,
           countQuery = """
            SELECT COUNT(*) FROM events e
            WHERE e.deleted_at IS NULL
              AND e.status = 'PUBLISHED'
              AND e.latitude  BETWEEN :minLat AND :maxLat
              AND e.longitude BETWEEN :minLng AND :maxLng
              AND ( :isAdmin = true OR e.university_id IS NULL OR e.university_id = :viewerUniversityId )
              AND (:category IS NULL OR e.category = :category)
              AND ( 6371 * 2 * ASIN(SQRT(
                      POWER(SIN(RADIANS(:lat - e.latitude) / 2), 2)
                    + COS(RADIANS(:lat)) * COS(RADIANS(e.latitude))
                    * POWER(SIN(RADIANS(:lng - e.longitude) / 2), 2)
                  )) ) <= :radiusKm
            """,
           nativeQuery = true)
    Page<Event> findNearby(@Param("lat") BigDecimal lat,
                           @Param("lng") BigDecimal lng,
                           @Param("minLat") BigDecimal minLat,
                           @Param("maxLat") BigDecimal maxLat,
                           @Param("minLng") BigDecimal minLng,
                           @Param("maxLng") BigDecimal maxLng,
                           @Param("radiusKm") double radiusKm,
                           @Param("isAdmin") boolean isAdmin,
                           @Param("viewerUniversityId") Long viewerUniversityId,
                           @Param("category") String category,
                           Pageable pageable);
}
