package com.unisphere.backend.commerce.services.repository;

import com.unisphere.backend.commerce.services.entity.ServiceOrder;
import com.unisphere.backend.commerce.services.enums.ServiceOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ServiceOrderRepository extends JpaRepository<ServiceOrder, Long> {

    /** Order roster for one listing, optionally filtered by status — the provider reviewing requests. */
    @Query("""
            SELECT o FROM ServiceOrder o
            WHERE o.listingId = :listingId
              AND (:status IS NULL OR o.status = :status)
            """)
    Page<ServiceOrder> findByListing(@Param("listingId") Long listingId,
                                     @Param("status") ServiceOrderStatus status,
                                     Pageable pageable);

    /** "My orders" as a client, across every listing, optionally filtered by status. */
    @Query("""
            SELECT o FROM ServiceOrder o
            WHERE o.clientId = :clientId
              AND (:status IS NULL OR o.status = :status)
            """)
    Page<ServiceOrder> findByClient(@Param("clientId") Long clientId,
                                    @Param("status") ServiceOrderStatus status,
                                    Pageable pageable);

    /**
     * "My orders received" as a provider, across every listing they own. No JPA relationship
     * mapping exists in this codebase (see {@code ServiceListing}/{@code ServiceOrder}'s plain
     * {@code Long} FK columns), so the ownership check is a JPQL subquery against
     * {@code ServiceListing} rather than a join.
     */
    @Query("""
            SELECT o FROM ServiceOrder o
            WHERE o.listingId IN (SELECT l.id FROM ServiceListing l WHERE l.providerId = :providerId)
              AND (:status IS NULL OR o.status = :status)
            """)
    Page<ServiceOrder> findReceivedByProvider(@Param("providerId") Long providerId,
                                              @Param("status") ServiceOrderStatus status,
                                              Pageable pageable);

    /** Backs {@code ServiceListingStatsResponse} — one grouped query, zero-filled in Java via EnumMap. */
    @Query("""
            SELECT o.status AS status, COUNT(o) AS total FROM ServiceOrder o
            WHERE o.listingId = :listingId
            GROUP BY o.status
            """)
    List<StatusCount> countByListingIdGroupByStatus(@Param("listingId") Long listingId);

    interface StatusCount {
        ServiceOrderStatus getStatus();
        long getTotal();
    }

    /** {@code ServiceListingService.deleteListing}'s guard — blocked while any order is non-terminal. */
    boolean existsByListingIdAndStatusIn(Long listingId, Collection<ServiceOrderStatus> statuses);
}
