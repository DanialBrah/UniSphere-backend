package com.unisphere.backend.campus.news.repository;

import com.unisphere.backend.campus.news.entity.NewsCommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;

public interface NewsCommentLikeRepository extends JpaRepository<NewsCommentLike, Long> {

    boolean existsByCommentIdAndUserId(Long commentId, Long userId);

    /** Batched form of {@link #existsByCommentIdAndUserId} for rendering a whole page of comments. */
    @Query("SELECT cl.commentId FROM NewsCommentLike cl WHERE cl.userId = :userId AND cl.commentId IN :commentIds")
    Set<Long> findLikedCommentIds(@Param("userId") Long userId,
                                  @Param("commentIds") Collection<Long> commentIds);

    void deleteByCommentIdAndUserId(Long commentId, Long userId);

    long countByCommentId(Long commentId);
}
