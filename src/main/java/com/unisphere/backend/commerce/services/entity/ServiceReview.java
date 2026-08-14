package com.unisphere.backend.commerce.services.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * One reviewer's rating of the other side of a {@code COMPLETED} {@link ServiceOrder} — at most one
 * per {@code (order, reviewer)} pair (see {@code uq_svcreview_order_reviewer}). Immutable — no edit
 * endpoint, no {@code updatedAt}. Only client-authored reviews (of the provider) roll into
 * {@code ServiceListing.ratingAvg}/{@code ratingCount} — see
 * {@code ServiceReviewService.recomputeListingRating}.
 */
@Entity
@Table(name = "service_reviews")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ServiceReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    @Column(name = "reviewee_id", nullable = false)
    private Long revieweeId;

    /** 1-5, validated via {@code @Min}/{@code @Max} on the request DTO, not a DB CHECK. */
    @Column(nullable = false)
    private int rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
