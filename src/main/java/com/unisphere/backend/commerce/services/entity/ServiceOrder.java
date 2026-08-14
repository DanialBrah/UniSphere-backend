package com.unisphere.backend.commerce.services.entity;

import com.unisphere.backend.commerce.services.enums.ServiceOrderStatus;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A client's formal request against a {@link ServiceListing}. Audit/transaction history — never
 * soft-deleted, so this entity carries no {@code @SQLRestriction}; terminal states
 * (COMPLETED/CANCELLED/DISPUTED) preserve the row, matching {@code JobApplication}.
 *
 * <p>{@link #agreedPrice} is nullable to support the {@code NEGOTIABLE} pricing flow — see the
 * header comment on migration {@code 022-03}. {@link #conversationId} links to the existing
 * messaging module: order creation auto-creates/reuses a DIRECT conversation with the provider via
 * {@code ConversationService}, the concrete "reach out via messaging" mechanism — see
 * {@code ServiceOrderService.createOrder}.
 */
@Entity
@Table(name = "service_orders")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ServiceOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(columnDefinition = "TEXT")
    private String requirements;

    /** Nullable — see the class javadoc and migration {@code 022-03}'s header note. */
    @Column(name = "agreed_price", precision = 10, scale = 2)
    private BigDecimal agreedPrice;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 11)
    private ServiceOrderStatus status = ServiceOrderStatus.PENDING;

    /** Optional note on decline/cancel/dispute — mirrors {@code job_applications.decision_reason}. */
    @Column(name = "decision_reason", length = 255)
    private String decisionReason;

    @Column(name = "conversation_id")
    private Long conversationId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
