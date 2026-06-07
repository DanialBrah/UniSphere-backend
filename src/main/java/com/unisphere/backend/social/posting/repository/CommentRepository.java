package com.unisphere.backend.social.posting.repository;

import com.unisphere.backend.social.posting.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    Page<Comment> findByPostIdAndParentCommentIdIsNullOrderByCreatedAtAsc(Long postId, Pageable pageable);

    Page<Comment> findByParentCommentIdOrderByCreatedAtAsc(Long parentCommentId, Pageable pageable);

    long countByPostIdAndParentCommentIdIsNull(Long postId);

    long countByParentCommentId(Long parentCommentId);

    @Modifying
    @Query("UPDATE Comment c SET c.likesCount = c.likesCount + :delta WHERE c.id = :id")
    void incrementLikesCount(@Param("id") Long id, @Param("delta") long delta);
}
