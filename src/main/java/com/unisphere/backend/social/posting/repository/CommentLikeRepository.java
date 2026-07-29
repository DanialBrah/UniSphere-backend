package com.unisphere.backend.social.posting.repository;

import com.unisphere.backend.social.posting.entity.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;

public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {

    boolean existsByCommentIdAndUserId(Long commentId, Long userId);

    /** Batched form of {@link #existsByCommentIdAndUserId} for rendering a whole page of comments. */
    @Query("SELECT cl.commentId FROM CommentLike cl WHERE cl.userId = :userId AND cl.commentId IN :commentIds")
    Set<Long> findLikedCommentIds(@Param("userId") Long userId, @Param("commentIds") Collection<Long> commentIds);

    void deleteByCommentIdAndUserId(Long commentId, Long userId);

    long countByCommentId(Long commentId);
}
