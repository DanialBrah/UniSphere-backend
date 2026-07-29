package com.unisphere.backend.social.follow.repository;

import com.unisphere.backend.social.follow.entity.Follow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface FollowRepository extends JpaRepository<Follow, Long> {
    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

    void deleteByFollowerIdAndFollowingId(Long followerId, Long followingId);

    /** How many people follow this user. */
    long countByFollowingId(Long followingId);

    /** How many people this user follows. */
    long countByFollowerId(Long followerId);

    /**
     * Which of {@code candidateIds} the given user already follows — resolved in one query so a
     * list of N users costs one round trip instead of N {@code existsBy} calls.
     */
    @Query("SELECT f.followingId FROM Follow f WHERE f.followerId = :followerId AND f.followingId IN :candidateIds")
    List<Long> findFollowedIdsAmong(@Param("followerId") Long followerId,
                                    @Param("candidateIds") Collection<Long> candidateIds);

    /** IDs of users {@code userId} follows, newest first. */
    @Query("SELECT f.followingId FROM Follow f WHERE f.followerId = :userId ORDER BY f.createdAt DESC")
    Page<Long> findFollowingIds(@Param("userId") Long userId, Pageable pageable);

    /** IDs of users who follow {@code userId}, newest first. */
    @Query("SELECT f.followerId FROM Follow f WHERE f.followingId = :userId ORDER BY f.createdAt DESC")
    Page<Long> findFollowerIds(@Param("userId") Long userId, Pageable pageable);

    /**
     * "People you may know": users the requester does not already follow, ranked so that
     * friends-of-friends (followed by someone the requester follows) come first, ordered by how
     * many such mutual connections they have, then backfilled with users from the same university.
     * <p>
     * Native because university_id lives on the role-specific tables under JOINED inheritance —
     * only students, alumni and clubs have one, hence the COALESCE across three LEFT JOINs.
     * {@code universityId} is null for employers/universities/admins, in which case the
     * same-university branch matches nothing and only mutuals are returned.
     */
    @Query(value = """
            SELECT u.id
            FROM users u
            LEFT JOIN students s ON s.user_id = u.id
            LEFT JOIN alumni   a ON a.user_id = u.id
            LEFT JOIN clubs    c ON c.user_id = u.id
            WHERE u.deleted_at IS NULL
              AND u.status = 'ACTIVE'
              AND u.id <> :userId
              AND NOT EXISTS (
                    SELECT 1 FROM follows f
                    WHERE f.follower_id = :userId AND f.following_id = u.id)
              AND (
                    EXISTS (
                        SELECT 1 FROM follows fof
                        WHERE fof.following_id = u.id
                          AND fof.follower_id IN (
                                SELECT mine.following_id FROM follows mine
                                WHERE mine.follower_id = :userId))
                 OR (:universityId IS NOT NULL
                     AND COALESCE(s.university_id, a.university_id, c.university_id) = :universityId)
              )
            ORDER BY (
                SELECT COUNT(*) FROM follows fof2
                WHERE fof2.following_id = u.id
                  AND fof2.follower_id IN (
                        SELECT mine2.following_id FROM follows mine2
                        WHERE mine2.follower_id = :userId)
            ) DESC, u.id ASC
            LIMIT :maxResults
            """,
            nativeQuery = true)
    List<Long> findRecommendedUserIds(@Param("userId") Long userId,
                                      @Param("universityId") Long universityId,
                                      @Param("maxResults") int maxResults);
}
