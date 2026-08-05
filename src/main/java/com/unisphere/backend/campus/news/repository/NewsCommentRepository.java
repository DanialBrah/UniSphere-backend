package com.unisphere.backend.campus.news.repository;

import com.unisphere.backend.campus.news.entity.NewsComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NewsCommentRepository extends JpaRepository<NewsComment, Long> {

    Page<NewsComment> findByArticleIdAndParentCommentIdIsNullOrderByCreatedAtAsc(Long articleId,
                                                                                Pageable pageable);

    Page<NewsComment> findByParentCommentIdOrderByCreatedAtAsc(Long parentCommentId, Pageable pageable);

    long countByArticleIdAndParentCommentIdIsNull(Long articleId);

    long countByParentCommentId(Long parentCommentId);

    // Explicit null check needed since @SQLRestriction filters soft-deleted rows,
    // but we need to fetch by ID for the delete operation before setting deleted_at
    @Query("SELECT c FROM NewsComment c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<NewsComment> findActiveById(@Param("id") Long id);

    /**
     * Batched form of {@link #countByArticleIdAndParentCommentIdIsNull}. Articles with no comments
     * are simply absent from the result rather than reported as zero — callers default them.
     */
    @Query("""
            SELECT c.articleId AS id, COUNT(c) AS total FROM NewsComment c
            WHERE c.articleId IN :articleIds AND c.parentCommentId IS NULL
            GROUP BY c.articleId
            """)
    List<CountByKey> countTopLevelByArticleIds(@Param("articleIds") Collection<Long> articleIds);

    /** Batched form of {@link #countByParentCommentId}. */
    @Query("""
            SELECT c.parentCommentId AS id, COUNT(c) AS total FROM NewsComment c
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
    @Query("UPDATE NewsComment c SET c.likesCount = c.likesCount + :delta WHERE c.id = :id")
    void incrementLikesCount(@Param("id") Long id, @Param("delta") long delta);
}
