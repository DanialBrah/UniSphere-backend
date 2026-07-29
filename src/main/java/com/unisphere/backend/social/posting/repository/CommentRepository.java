package com.unisphere.backend.social.posting.repository;

import com.unisphere.backend.social.posting.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    Page<Comment> findByPostIdAndParentCommentIdIsNullOrderByCreatedAtAsc(Long postId, Pageable pageable);

    Page<Comment> findByParentCommentIdOrderByCreatedAtAsc(Long parentCommentId, Pageable pageable);

    long countByPostIdAndParentCommentIdIsNull(Long postId);

    long countByParentCommentId(Long parentCommentId);

    /**
     * Batched form of {@link #countByPostIdAndParentCommentIdIsNull}. Posts with no comments are
     * simply absent from the result rather than reported as zero — callers default them.
     */
    @Query("""
            SELECT c.postId AS id, COUNT(c) AS total FROM Comment c
            WHERE c.postId IN :postIds AND c.parentCommentId IS NULL
            GROUP BY c.postId
            """)
    List<CountByKey> countTopLevelByPostIds(@Param("postIds") Collection<Long> postIds);

    /** Batched form of {@link #countByParentCommentId}. */
    @Query("""
            SELECT c.parentCommentId AS id, COUNT(c) AS total FROM Comment c
            WHERE c.parentCommentId IN :parentIds
            GROUP BY c.parentCommentId
            """)
    List<CountByKey> countRepliesByParentIds(@Param("parentIds") Collection<Long> parentIds);

    /** Projection for the grouped counts above. */
    interface CountByKey {
        Long getId();
        long getTotal();
    }

    @Modifying
    @Transactional
    @Query("UPDATE Comment c SET c.likesCount = c.likesCount + :delta WHERE c.id = :id")
    void incrementLikesCount(@Param("id") Long id, @Param("delta") long delta);
}
