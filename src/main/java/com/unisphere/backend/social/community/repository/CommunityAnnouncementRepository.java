package com.unisphere.backend.social.community.repository;

import com.unisphere.backend.social.community.entity.CommunityAnnouncement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CommunityAnnouncementRepository extends JpaRepository<CommunityAnnouncement, Long> {

    // Explicit null check needed since @SQLRestriction filters soft-deleted rows,
    // but we need to fetch by ID for the delete operation before setting deleted_at
    @Query("SELECT a FROM CommunityAnnouncement a WHERE a.id = :id AND a.deletedAt IS NULL")
    Optional<CommunityAnnouncement> findActiveById(@Param("id") Long id);

    Page<CommunityAnnouncement> findByCommunityIdOrderByPinnedDescCreatedAtDesc(Long communityId, Pageable pageable);

    /**
     * Bulk soft-delete, used when a community itself is deleted. Bypassed by @SQLRestriction like
     * every other bulk HQL update, so deletedAt IS NULL is mandatory or an already-deleted
     * announcement would be re-stamped.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE CommunityAnnouncement a
               SET a.deletedAt = :now
             WHERE a.communityId = :communityId
               AND a.deletedAt IS NULL
            """)
    void softDeleteByCommunityId(@Param("communityId") Long communityId, @Param("now") LocalDateTime now);
}
