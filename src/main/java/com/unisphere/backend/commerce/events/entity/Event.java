package com.unisphere.backend.commerce.events.entity;

import com.unisphere.backend.commerce.events.enums.EventCategory;
import com.unisphere.backend.commerce.events.enums.EventRegistrationMode;
import com.unisphere.backend.commerce.events.enums.EventStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single-occurrence campus event. Organized by any authenticated user (no role restriction, same
 * as {@code marketplace_items}/{@code jobs}/{@code posts}), scoped to the organizer's affiliated
 * university unless the organizer has none (Employer, Admin), in which case it is visible everywhere
 * — see {@code EventAccessService.resolveEventUniversityId}.
 *
 * <p>{@code registeredCount}/{@code waitlistedCount} are denormalised counters, maintained
 * transactionally inside {@code EventRegistrationService} alongside every registration write, which
 * already holds a pessimistic lock on this row for the capacity check. Never mutate them outside
 * that lock — see {@code EventRepository.findByIdForUpdate}.
 */
@Entity
@Table(name = "events")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organizer_id", nullable = false)
    private Long organizerId;

    /**
     * Always derived server-side from the organizer's own affiliation, never from the request —
     * same rule as {@code LostFoundItem.universityId}. Null for organizers with no affiliation
     * (Employer, Admin), which the feed predicate treats as visible to everyone.
     */
    @Column(name = "university_id")
    private Long universityId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 11)
    private EventCategory category = EventCategory.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 9)
    private EventStatus status = EventStatus.DRAFT;

    /** Bare object key (e.g. events/9/uuid.jpeg) — never a resolved URL; see changeset 012. */
    @Column(name = "cover_image_key", length = 500)
    private String coverImageKey;

    @Column(name = "start_datetime", nullable = false)
    private LocalDateTime startDatetime;

    @Column(name = "end_datetime", nullable = false)
    private LocalDateTime endDatetime;

    @Column(name = "is_online", nullable = false)
    private boolean online;

    /** Required iff {@link #online}. */
    @Column(name = "online_url", length = 500)
    private String onlineUrl;

    /** Required iff not {@link #online}. */
    @Column(name = "venue_name", length = 255)
    private String venueName;

    // BigDecimal, never Double — see LostFoundItem's precedent: a double field renders
    // float/double precision, matching neither Hibernate's JDBC type code nor its `decimal`
    // name-prefix check, and throws SchemaManagementException at context refresh, taking the whole
    // application down. precision/scale are not themselves validated by Hibernate 6 but make the
    // generated DDL correct.
    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_mode", nullable = false, length = 8)
    private EventRegistrationMode registrationMode = EventRegistrationMode.INTERNAL;

    /** Required iff {@code registrationMode == EXTERNAL}. */
    @Column(name = "external_registration_url", length = 500)
    private String externalRegistrationUrl;

    /** Null = unlimited. Must be null when {@code registrationMode == EXTERNAL}. */
    @Column(name = "max_capacity")
    private Integer maxCapacity;

    @Column(name = "registered_count", nullable = false)
    private int registeredCount = 0;

    @Column(name = "waitlisted_count", nullable = false)
    private int waitlistedCount = 0;

    /** Set once by {@code EventReminderScheduler}; guards against duplicate reminder sends. */
    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
