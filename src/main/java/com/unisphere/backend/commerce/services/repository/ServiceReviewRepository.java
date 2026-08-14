package com.unisphere.backend.commerce.services.repository;

import com.unisphere.backend.commerce.services.entity.ServiceReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ServiceReviewRepository extends JpaRepository<ServiceReview, Long> {

    boolean existsByOrderIdAndReviewerId(Long orderId, Long reviewerId);

    /** Both sides' reviews for one order. */
    List<ServiceReview> findByOrderId(Long orderId);

    /**
     * Public, client-authored reviews of a listing's provider. No JPA relationship mapping exists
     * in this codebase, so the {@code order -> listing} link is a JPQL subquery against
     * {@code ServiceOrder} rather than a join.
     */
    @Query("""
            SELECT r FROM ServiceReview r
            WHERE r.orderId IN (SELECT o.id FROM ServiceOrder o WHERE o.listingId = :listingId)
              AND r.revieweeId = :providerId
            ORDER BY r.createdAt DESC
            """)
    Page<ServiceReview> findByListingAndProviderReviewee(@Param("listingId") Long listingId,
                                                          @Param("providerId") Long providerId,
                                                          Pageable pageable);

    /**
     * Aggregate for {@code ServiceReviewService.recomputeListingRating} — average and count of
     * every client-authored review (reviewee = the listing's provider) across the listing's orders.
     */
    @Query("""
            SELECT AVG(r.rating), COUNT(r) FROM ServiceReview r
            WHERE r.orderId IN (SELECT o.id FROM ServiceOrder o WHERE o.listingId = :listingId)
              AND r.revieweeId = :providerId
            """)
    Object[] aggregateRatingForListing(@Param("listingId") Long listingId, @Param("providerId") Long providerId);
}
