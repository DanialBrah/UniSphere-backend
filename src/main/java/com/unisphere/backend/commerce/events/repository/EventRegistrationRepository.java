package com.unisphere.backend.commerce.events.repository;

import com.unisphere.backend.commerce.events.entity.EventRegistration;
import com.unisphere.backend.commerce.events.enums.EventRegistrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EventRegistrationRepository extends JpaRepository<EventRegistration, Long> {

    /**
     * The caller's registration for one event — "first by createdAt desc", not a plain lookup by
     * (eventId, userId), because there is deliberately no unique constraint on that pair (see the
     * {@code event_registrations} migration note): a cancel-then-re-register sequence leaves more
     * than one historical row, and this is the most recent one.
     */
    Optional<EventRegistration> findFirstByEventIdAndUserIdOrderByCreatedAtDesc(Long eventId, Long userId);

    /** The "at most one active registration per (event, user)" guard the table deliberately lacks. */
    boolean existsByEventIdAndUserIdAndStatusIn(Long eventId, Long userId, Collection<EventRegistrationStatus> statuses);

    /** Attendee list / check-in roster, optionally filtered by status. */
    @Query("""
            SELECT r FROM EventRegistration r
            WHERE r.eventId = :eventId
              AND (:status IS NULL OR r.status = :status)
            """)
    Page<EventRegistration> findByEvent(@Param("eventId") Long eventId,
                                        @Param("status") EventRegistrationStatus status,
                                        Pageable pageable);

    /** "My tickets" across every event, optionally filtered by status. */
    @Query("""
            SELECT r FROM EventRegistration r
            WHERE r.userId = :userId
              AND (:status IS NULL OR r.status = :status)
            """)
    Page<EventRegistration> findByUser(@Param("userId") Long userId,
                                       @Param("status") EventRegistrationStatus status,
                                       Pageable pageable);

    /** FIFO waitlist promotion candidate — first in line wins, no scoring or preference logic. */
    Optional<EventRegistration> findFirstByEventIdAndStatusOrderByCreatedAtAsc(Long eventId, EventRegistrationStatus status);

    /**
     * Check-in lookup. {@code ticketCode} is already globally unique, so {@code eventId} is
     * defense-in-depth: it catches a ticket code copy-pasted from a different event rather than
     * silently checking someone in at the wrong door.
     */
    Optional<EventRegistration> findByEventIdAndTicketCode(Long eventId, String ticketCode);

    /** {@code EventReminderScheduler}'s fan-out list — ids only, no need to hydrate full rows. */
    @Query("SELECT r.userId FROM EventRegistration r WHERE r.eventId = :eventId AND r.status = :status")
    List<Long> findUserIdsByEventIdAndStatus(@Param("eventId") Long eventId,
                                             @Param("status") EventRegistrationStatus status);

    /** Backs {@code EventStatsResponse} — one grouped query, zero-filled in Java via EnumMap. */
    @Query("""
            SELECT r.status AS status, COUNT(r) AS total FROM EventRegistration r
            WHERE r.eventId = :eventId
            GROUP BY r.status
            """)
    List<StatusCount> countByEventIdGroupByStatus(@Param("eventId") Long eventId);

    interface StatusCount {
        EventRegistrationStatus getStatus();
        long getTotal();
    }

    /**
     * The feed's batch "am I registered, and with what status" lookup — one query for the whole
     * page, loaded into a {@code Map<eventId, status>} rather than once per row.
     */
    List<EventRegistration> findByEventIdInAndUserId(Collection<Long> eventIds, Long userId);
}
