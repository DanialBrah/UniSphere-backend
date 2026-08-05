package com.unisphere.backend.campus.news.repository;

import com.unisphere.backend.campus.news.entity.NewsSave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;

public interface NewsSaveRepository extends JpaRepository<NewsSave, Long> {

    boolean existsByUserIdAndArticleId(Long userId, Long articleId);

    /** Batched form of {@link #existsByUserIdAndArticleId} for rendering a whole page of articles. */
    @Query("SELECT s.articleId FROM NewsSave s WHERE s.userId = :userId AND s.articleId IN :articleIds")
    Set<Long> findSavedArticleIds(@Param("userId") Long userId,
                                  @Param("articleIds") Collection<Long> articleIds);

    void deleteByUserIdAndArticleId(Long userId, Long articleId);
}
