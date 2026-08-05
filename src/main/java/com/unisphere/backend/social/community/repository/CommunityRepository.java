package com.unisphere.backend.social.community.repository;

import com.unisphere.backend.social.community.entity.Community;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface CommunityRepository extends JpaRepository<Community, Long> {

    // Explicit null check needed since @SQLRestriction filters soft-deleted rows,
    // but we need to fetch by ID for the delete operation before setting deleted_at
    @Query("SELECT c FROM Community c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<Community> findActiveById(@Param("id") Long id);

    /**
     * FULLTEXT search over name/description. Deliberately not visibility-filtered: a community's
     * existence and basic info (name, description, member count) is discoverable regardless of
     * visibility — a PRIVATE community must be findable, or its join-request workflow has no way
     * to start. Visibility instead gates *content* (members, posts, announcements, chat) via
     * {@link CommunityAccessService#canViewContent}.
     */
    @Query(value = """
            SELECT * FROM communities c
            WHERE MATCH(c.name, c.description) AGAINST (:query IN BOOLEAN MODE)
              AND c.deleted_at IS NULL
            """,
            countQuery = """
            SELECT COUNT(*) FROM communities c
            WHERE MATCH(c.name, c.description) AGAINST (:query IN BOOLEAN MODE)
              AND c.deleted_at IS NULL
            """,
            nativeQuery = true)
    Page<Community> search(@Param("query") String query, Pageable pageable);

    /** Communities the caller belongs to — "my communities". */
    @Query("""
            SELECT c FROM Community c
            JOIN CommunityMember cm ON cm.communityId = c.id
            WHERE cm.userId = :userId
            ORDER BY cm.joinedAt DESC
            """)
    Page<Community> findAllByMemberId(@Param("userId") Long userId, Pageable pageable);

    /**
     * Direct atomic update, not a Redis-buffered counter like likes/views — join/leave is
     * low-volume and the member count should be exactly right immediately after a change.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Community c SET c.memberCount = c.memberCount + :delta WHERE c.id = :id")
    void incrementMemberCount(@Param("id") Long id, @Param("delta") int delta);
}
