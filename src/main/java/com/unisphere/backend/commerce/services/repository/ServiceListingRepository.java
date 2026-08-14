package com.unisphere.backend.commerce.services.repository;

import com.unisphere.backend.commerce.services.entity.ServiceListing;
import com.unisphere.backend.commerce.services.enums.ServiceDeliveryMode;
import com.unisphere.backend.commerce.services.enums.ServiceListingStatus;
import com.unisphere.backend.commerce.services.enums.ServicePricingType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ServiceListingRepository extends JpaRepository<ServiceListing, Long> {

    // Explicit null check needed since @SQLRestriction filters soft-deleted rows,
    // but we need to fetch by ID for the delete/update path before evaluating it.
    @Query("SELECT l FROM ServiceListing l WHERE l.id = :id AND l.deletedAt IS NULL")
    Optional<ServiceListing> findActiveById(@Param("id") Long id);

    /**
     * The browse feed. {@code status} is always a concrete, resolved value by the time this is
     * called — {@code ServiceListingService.getFeed} defaults a null filter to ACTIVE and rejects a
     * client-supplied PAUSED outright, so PAUSED can never reach this query.
     *
     * <p>isAdmin/viewerUniversityId are precomputed once per call in the service, so the visibility
     * predicate stays a plain column comparison and pagination totals remain exact.
     */
    @Query("""
            SELECT l FROM ServiceListing l
            WHERE l.status = :status
              AND ( :isAdmin = true
                    OR l.universityId IS NULL
                    OR l.universityId = :viewerUniversityId )
              AND (:category      IS NULL OR l.category      = :category)
              AND (:pricingType   IS NULL OR l.pricingType    = :pricingType)
              AND (:deliveryMode  IS NULL OR l.deliveryMode   = :deliveryMode)
              AND (:universityId  IS NULL OR l.universityId   = :universityId)
            """)
    Page<ServiceListing> findFeed(@Param("isAdmin") boolean isAdmin,
                                  @Param("viewerUniversityId") Long viewerUniversityId,
                                  @Param("status") ServiceListingStatus status,
                                  @Param("category") String category,
                                  @Param("pricingType") ServicePricingType pricingType,
                                  @Param("deliveryMode") ServiceDeliveryMode deliveryMode,
                                  @Param("universityId") Long universityId,
                                  Pageable pageable);

    /** The caller's own listings, every status — no visibility predicate, they are all theirs. */
    @Query("""
            SELECT l FROM ServiceListing l
            WHERE l.providerId = :providerId
              AND (:status IS NULL OR l.status = :status)
            """)
    Page<ServiceListing> findByProviderId(@Param("providerId") Long providerId,
                                          @Param("status") ServiceListingStatus status,
                                          Pageable pageable);

    /**
     * FULLTEXT search over title and description. The service sanitises the query first: an
     * unbalanced BOOLEAN-MODE operator is a MySQL syntax error that would surface as a 500 on a
     * plain user search. Native query, so deleted_at/status are hand-written and the caller strips
     * any client-supplied sort — same three obligations as {@code JobRepository.searchFullText}.
     */
    @Query(value = """
            SELECT * FROM service_listings l
            WHERE MATCH(l.title, l.description) AGAINST (:query IN BOOLEAN MODE)
              AND l.deleted_at IS NULL
              AND l.status = 'ACTIVE'
              AND ( :isAdmin = true OR l.university_id IS NULL OR l.university_id = :viewerUniversityId )
            ORDER BY l.created_at DESC, l.id DESC
            """,
           countQuery = """
            SELECT COUNT(*) FROM service_listings l
            WHERE MATCH(l.title, l.description) AGAINST (:query IN BOOLEAN MODE)
              AND l.deleted_at IS NULL
              AND l.status = 'ACTIVE'
              AND ( :isAdmin = true OR l.university_id IS NULL OR l.university_id = :viewerUniversityId )
            """,
           nativeQuery = true)
    Page<ServiceListing> searchFullText(@Param("query") String query,
                                        @Param("isAdmin") boolean isAdmin,
                                        @Param("viewerUniversityId") Long viewerUniversityId,
                                        Pageable pageable);
}
