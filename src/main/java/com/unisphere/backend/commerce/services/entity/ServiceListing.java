package com.unisphere.backend.commerce.services.entity;

import com.unisphere.backend.commerce.services.enums.ServiceDeliveryMode;
import com.unisphere.backend.commerce.services.enums.ServiceListingStatus;
import com.unisphere.backend.commerce.services.enums.ServicePricingType;
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
 * A service a provider offers. Posted only by {@code STUDENT}/{@code ALUMNI}/{@code CLUB} accounts
 * — see {@code ServiceAccessService.assertCanCreate}, the same role set {@code ProjectAccessService}
 * uses.
 *
 * <p>{@link #status} is a pure availability toggle (ACTIVE/PAUSED), orthogonal to soft delete —
 * unlike the reference sketch in {@code miscellaneous/unisphere_module4_services_addon.sql}, there
 * is no third REMOVED state; that's what {@link #deletedAt} is for, matching {@code Job}/
 * {@code Project}.
 *
 * <p>{@link #ratingAvg}/{@link #ratingCount} are denormalised, recalculated transactionally on each
 * new client-authored review — see {@code ServiceReviewService.recomputeListingRating}.
 */
@Entity
@Table(name = "service_listings")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ServiceListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    /** Null = visible to all universities. Derived from the provider's own affiliation, never client-supplied. */
    @Column(name = "university_id")
    private Long universityId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 100)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_type", nullable = false, length = 10)
    private ServicePricingType pricingType = ServicePricingType.FIXED;

    /** Required for FIXED/HOURLY, forbidden for NEGOTIABLE — enforced in {@code ServiceListingService}. */
    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_mode", nullable = false, length = 8)
    private ServiceDeliveryMode deliveryMode = ServiceDeliveryMode.BOTH;

    /** Bare object key, never a resolved URL — see changeset 012. */
    @Column(name = "portfolio_image_key", length = 500)
    private String portfolioImageKey;

    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private ServiceListingStatus status = ServiceListingStatus.ACTIVE;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
