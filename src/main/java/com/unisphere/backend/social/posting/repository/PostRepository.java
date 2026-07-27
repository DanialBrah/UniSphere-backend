package com.unisphere.backend.social.posting.repository;

import com.unisphere.backend.social.posting.entity.Post;
import com.unisphere.backend.social.posting.enums.PostVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    Page<Post> findByVisibilityOrderByCreatedAtDesc(PostVisibility visibility, Pageable pageable);

    // Precomputed once per call (single profile owner), so pagination totals stay exact — see
    // isFriend/viewerUniversityId/isOwnerOrAdmin computation in PostService.getPostsByUser.
    @Query("""
            SELECT p FROM Post p
            WHERE p.userId = :profileUserId
              AND ( :isOwnerOrAdmin = true
                    OR p.visibility = com.unisphere.backend.social.posting.enums.PostVisibility.PUBLIC
                    OR (p.visibility = com.unisphere.backend.social.posting.enums.PostVisibility.UNIVERSITY
                        AND p.universityId = :viewerUniversityId)
                    OR (p.visibility = com.unisphere.backend.social.posting.enums.PostVisibility.FRIENDS
                        AND :isFriend = true)
                  )
            ORDER BY p.createdAt DESC
            """)
    Page<Post> findByUserIdVisibleTo(@Param("profileUserId") Long profileUserId,
                                      @Param("isOwnerOrAdmin") boolean isOwnerOrAdmin,
                                      @Param("viewerUniversityId") Long viewerUniversityId,
                                      @Param("isFriend") boolean isFriend,
                                      Pageable pageable);

    // Explicit null check needed since @SQLRestriction filters soft-deleted rows,
    // but we need to fetch by ID for the delete operation before setting deleted_at
    @Query("SELECT p FROM Post p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<Post> findActiveById(@Param("id") Long id);

    // Viewer and searcher are always the same person here, so a single userId/isAdmin/
    // viewerUniversityId set (not two) is enough; FRIENDS uses a correlated EXISTS against
    // follows keyed on p.user_id (varies per row), which a precomputed boolean can't express.
    @Query(value = """
            SELECT * FROM posts p
            WHERE MATCH(p.title, p.content) AGAINST (:query IN BOOLEAN MODE)
              AND p.deleted_at IS NULL
              AND ( p.user_id = :userId
                    OR :isAdmin = true
                    OR p.visibility = 'PUBLIC'
                    OR (p.visibility = 'UNIVERSITY' AND p.university_id = :viewerUniversityId)
                    OR (p.visibility = 'FRIENDS' AND EXISTS (
                          SELECT 1 FROM follows f1
                          JOIN follows f2 ON f2.follower_id = f1.following_id
                                          AND f2.following_id = f1.follower_id
                          WHERE f1.follower_id = :userId AND f1.following_id = p.user_id
                        ))
                  )
            """,
            countQuery = """
            SELECT COUNT(*) FROM posts p
            WHERE MATCH(p.title, p.content) AGAINST (:query IN BOOLEAN MODE)
              AND p.deleted_at IS NULL
              AND ( p.user_id = :userId
                    OR :isAdmin = true
                    OR p.visibility = 'PUBLIC'
                    OR (p.visibility = 'UNIVERSITY' AND p.university_id = :viewerUniversityId)
                    OR (p.visibility = 'FRIENDS' AND EXISTS (
                          SELECT 1 FROM follows f1
                          JOIN follows f2 ON f2.follower_id = f1.following_id
                                          AND f2.following_id = f1.follower_id
                          WHERE f1.follower_id = :userId AND f1.following_id = p.user_id
                        ))
                  )
            """,
            nativeQuery = true)
    Page<Post> searchFullText(@Param("query") String query,
                               @Param("userId") Long userId,
                               @Param("isAdmin") boolean isAdmin,
                               @Param("viewerUniversityId") Long viewerUniversityId,
                               Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE Post p SET p.likesCount = p.likesCount + :delta WHERE p.id = :id")
    void incrementLikesCount(@Param("id") Long id, @Param("delta") long delta);

    @Modifying
    @Transactional
    @Query("UPDATE Post p SET p.viewsCount = p.viewsCount + :delta WHERE p.id = :id")
    void incrementViewsCount(@Param("id") Long id, @Param("delta") long delta);

    // Viewer and "whose likes we're listing" are always the same person (no "view someone else's
    // likes" endpoint exists), so one userId/isAdmin/viewerUniversityId set covers both roles.
    @Query(value = """
            SELECT p FROM Post p, PostLike pl
            WHERE pl.postId = p.id AND pl.userId = :userId
              AND ( p.userId = :userId
                    OR :isAdmin = true
                    OR p.visibility = com.unisphere.backend.social.posting.enums.PostVisibility.PUBLIC
                    OR (p.visibility = com.unisphere.backend.social.posting.enums.PostVisibility.UNIVERSITY
                        AND p.universityId = :viewerUniversityId)
                    OR (p.visibility = com.unisphere.backend.social.posting.enums.PostVisibility.FRIENDS
                        AND EXISTS (
                              SELECT 1 FROM Follow f1, Follow f2
                              WHERE f1.followerId = :userId AND f1.followingId = p.userId
                                AND f2.followerId = p.userId AND f2.followingId = :userId
                            ))
                  )
            ORDER BY pl.createdAt DESC
            """,
           countQuery = """
            SELECT COUNT(p) FROM Post p, PostLike pl
            WHERE pl.postId = p.id AND pl.userId = :userId
              AND ( p.userId = :userId
                    OR :isAdmin = true
                    OR p.visibility = com.unisphere.backend.social.posting.enums.PostVisibility.PUBLIC
                    OR (p.visibility = com.unisphere.backend.social.posting.enums.PostVisibility.UNIVERSITY
                        AND p.universityId = :viewerUniversityId)
                    OR (p.visibility = com.unisphere.backend.social.posting.enums.PostVisibility.FRIENDS
                        AND EXISTS (
                              SELECT 1 FROM Follow f1, Follow f2
                              WHERE f1.followerId = :userId AND f1.followingId = p.userId
                                AND f2.followerId = p.userId AND f2.followingId = :userId
                            ))
                  )
            """)
    Page<Post> findLikedPostsByUserId(@Param("userId") Long userId,
                                       @Param("isAdmin") boolean isAdmin,
                                       @Param("viewerUniversityId") Long viewerUniversityId,
                                       Pageable pageable);

    @Query(value = """
            SELECT p FROM Post p, PostSave ps
            WHERE ps.postId = p.id AND ps.userId = :userId
              AND ( p.userId = :userId
                    OR :isAdmin = true
                    OR p.visibility = com.unisphere.backend.social.posting.enums.PostVisibility.PUBLIC
                    OR (p.visibility = com.unisphere.backend.social.posting.enums.PostVisibility.UNIVERSITY
                        AND p.universityId = :viewerUniversityId)
                    OR (p.visibility = com.unisphere.backend.social.posting.enums.PostVisibility.FRIENDS
                        AND EXISTS (
                              SELECT 1 FROM Follow f1, Follow f2
                              WHERE f1.followerId = :userId AND f1.followingId = p.userId
                                AND f2.followerId = p.userId AND f2.followingId = :userId
                            ))
                  )
            ORDER BY ps.savedAt DESC
            """,
           countQuery = """
            SELECT COUNT(p) FROM Post p, PostSave ps
            WHERE ps.postId = p.id AND ps.userId = :userId
              AND ( p.userId = :userId
                    OR :isAdmin = true
                    OR p.visibility = com.unisphere.backend.social.posting.enums.PostVisibility.PUBLIC
                    OR (p.visibility = com.unisphere.backend.social.posting.enums.PostVisibility.UNIVERSITY
                        AND p.universityId = :viewerUniversityId)
                    OR (p.visibility = com.unisphere.backend.social.posting.enums.PostVisibility.FRIENDS
                        AND EXISTS (
                              SELECT 1 FROM Follow f1, Follow f2
                              WHERE f1.followerId = :userId AND f1.followingId = p.userId
                                AND f2.followerId = p.userId AND f2.followingId = :userId
                            ))
                  )
            """)
    Page<Post> findSavedPostsByUserId(@Param("userId") Long userId,
                                       @Param("isAdmin") boolean isAdmin,
                                       @Param("viewerUniversityId") Long viewerUniversityId,
                                       Pageable pageable);
}
