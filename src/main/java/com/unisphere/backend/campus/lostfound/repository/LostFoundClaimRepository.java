package com.unisphere.backend.campus.lostfound.repository;

import com.unisphere.backend.campus.lostfound.entity.LostFoundClaim;
import com.unisphere.backend.campus.lostfound.enums.LostFoundClaimStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface LostFoundClaimRepository extends JpaRepository<LostFoundClaim, Long> {

    @Query("""
            SELECT c FROM LostFoundClaim c
            WHERE c.itemId = :itemId
              AND (:status IS NULL OR c.status = :status)
            """)
    Page<LostFoundClaim> findByItem(@Param("itemId") Long itemId,
                                    @Param("status") LostFoundClaimStatus status,
                                    Pageable pageable);

    /** What a claimant (rather than the reporter) is allowed to see on someone else's item. */
    @Query("""
            SELECT c FROM LostFoundClaim c
            WHERE c.itemId = :itemId AND c.claimantId = :claimantId
              AND (:status IS NULL OR c.status = :status)
            """)
    Page<LostFoundClaim> findByItemAndClaimant(@Param("itemId") Long itemId,
                                               @Param("claimantId") Long claimantId,
                                               @Param("status") LostFoundClaimStatus status,
                                               Pageable pageable);

    @Query("""
            SELECT c FROM LostFoundClaim c
            WHERE c.claimantId = :claimantId
              AND (:status IS NULL OR c.status = :status)
            """)
    Page<LostFoundClaim> findByClaimant(@Param("claimantId") Long claimantId,
                                        @Param("status") LostFoundClaimStatus status,
                                        Pageable pageable);

    /** The "one active PENDING claim per (item, claimant)" guard the table deliberately lacks. */
    boolean existsByItemIdAndClaimantIdAndStatus(Long itemId, Long claimantId, LostFoundClaimStatus status);

    /** Fan-out target when a sibling claim is approved. */
    List<LostFoundClaim> findByItemIdAndStatus(Long itemId, LostFoundClaimStatus status);

    boolean existsByItemIdAndClaimantId(Long itemId, Long claimantId);

    long countByItemIdAndStatus(Long itemId, LostFoundClaimStatus status);

    long countByStatus(LostFoundClaimStatus status);

    /**
     * PageContext: which items on this page the viewer has claimed, and at what status. One query
     * for the whole page — the approved subset drives the privacy guard, so this must never become
     * per-row.
     */
    List<LostFoundClaim> findByItemIdInAndClaimantIdAndStatusIn(
            Collection<Long> itemIds, Long claimantId, Collection<LostFoundClaimStatus> statuses);

    /** PageContext: pending-claim count per item, one grouped query for the whole page. */
    @Query("""
            SELECT c.itemId AS id, COUNT(c) AS total FROM LostFoundClaim c
            WHERE c.itemId IN :itemIds AND c.status = :status
            GROUP BY c.itemId
            """)
    List<CountByKey> countByItemIdsAndStatus(@Param("itemIds") Collection<Long> itemIds,
                                             @Param("status") LostFoundClaimStatus status);

    interface CountByKey {
        Long getId();
        long getTotal();
    }
}
