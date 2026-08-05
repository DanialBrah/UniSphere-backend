package com.unisphere.backend.campus.news.repository;

import com.unisphere.backend.campus.news.entity.NewsLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;

public interface NewsLikeRepository extends JpaRepository<NewsLike, Long> {

    boolean existsByArticleIdAndUserId(Long articleId, Long userId);

    /** Batched form of {@link #existsByArticleIdAndUserId} for rendering a whole page of articles. */
    @Query("SELECT l.articleId FROM NewsLike l WHERE l.userId = :userId AND l.articleId IN :articleIds")
    Set<Long> findLikedArticleIds(@Param("userId") Long userId,
                                  @Param("articleIds") Collection<Long> articleIds);

    void deleteByArticleIdAndUserId(Long articleId, Long userId);

    long countByArticleId(Long articleId);
}
