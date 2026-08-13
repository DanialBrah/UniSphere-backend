package com.unisphere.backend.commerce.events.entity;

import com.unisphere.backend.commerce.events.enums.EventRegistrationStatus;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * One user's registration for one event. Audit/attendance history — never soft-deleted, so this
 * entity carries no {@code @SQLRestriction}; withdrawal is {@code status = CANCELLED}, matching
 * {@code LostFoundClaim}.
 *
 * <p>Deliberately no unique constraint on {@code (eventId, userId)} — a cancelled registrant may
 * re-register later. "At most one active row per (event, user)" is enforced in
 * {@code EventRegistrationService}, not the database. See {@code event_registrations} in migration
 * {@code 017} for the full rationale.
 */
@Entity
@Table(name = "event_registrations")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class EventRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EventRegistrationStatus status = EventRegistrationStatus.REGISTERED;

    /** {@code UUID.randomUUID().toString()} — a bare scannable string; QR rendering is client-side. */
    @Column(name = "ticket_code", nullable = false, length = 40)
    private String ticketCode;

    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    /** The organizer/admin who scanned the ticket at check-in. Null until then. */
    @Column(name = "checked_in_by")
    private Long checkedInBy;

    /**
     * Who cancelled this registration — the registrant themselves, or the event's organizer/admin.
     * Compare against {@link #userId} to tell a self-cancel apart from an organizer removal;
     * {@code EventRegistrationService.cancel} notifies the registrant only in the latter case.
     */
    @Column(name = "cancelled_by")
    private Long cancelledBy;

    /** Optional note left by an organizer/admin removing someone. Mirrors {@code LostFoundClaim.decisionNote}. */
    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
