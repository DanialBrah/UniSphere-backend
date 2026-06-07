package com.unisphere.backend.social.posting.repository;

import com.unisphere.backend.social.posting.entity.Post;
import com.unisphere.backend.social.posting.enums.PostVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    Page<Post> findByVisibilityOrderByCreatedAtDesc(PostVisibility visibility, Pageable pageable);

    Page<Post> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // Explicit null check needed since @SQLRestriction filters soft-deleted rows,
    // but we need to fetch by ID for the delete operation before setting deleted_at
    @Query("SELECT p FROM Post p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<Post> findActiveById(@Param("id") Long id);

    @Query(value = "SELECT * FROM posts WHERE MATCH(title, content) AGAINST (:query IN BOOLEAN MODE) AND deleted_at IS NULL",
            countQuery = "SELECT COUNT(*) FROM posts WHERE MATCH(title, content) AGAINST (:query IN BOOLEAN MODE) AND deleted_at IS NULL",
            nativeQuery = true)
    Page<Post> searchFullText(@Param("query") String query, Pageable pageable);

    @Modifying
    @Query("UPDATE Post p SET p.likesCount = p.likesCount + :delta WHERE p.id = :id")
    void incrementLikesCount(@Param("id") Long id, @Param("delta") long delta);

    @Modifying
    @Query("UPDATE Post p SET p.viewsCount = p.viewsCount + :delta WHERE p.id = :id")
    void incrementViewsCount(@Param("id") Long id, @Param("delta") long delta);
}
