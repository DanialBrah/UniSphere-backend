package com.unisphere.backend.campus.news.repository;

import com.unisphere.backend.campus.news.entity.NewsTag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NewsTagRepository extends JpaRepository<NewsTag, Long> {

    /**
     * Most-used tags, for a tag cloud.
     *
     * <p>Visibility-filtered on purpose: a naive {@code SELECT DISTINCT tag} would leak the tags of
     * draft and university-scoped articles to everyone.
     */
    @Query("""
            SELECT t.tag AS tag, COUNT(t) AS total
            FROM NewsTag t JOIN t.article a
            WHERE a.status = com.unisphere.backend.campus.news.enums.NewsStatus.PUBLISHED
              AND ( :isAdmin = true
                    OR a.visibility = com.unisphere.backend.campus.news.enums.NewsVisibility.PUBLIC
                    OR (a.visibility = com.unisphere.backend.campus.news.enums.NewsVisibility.UNIVERSITY
                        AND a.universityId = :viewerUniversityId) )
            GROUP BY t.tag
            ORDER BY COUNT(t) DESC, t.tag ASC
            """)
    List<TagCount> findPopularTags(@Param("isAdmin") boolean isAdmin,
                                   @Param("viewerUniversityId") Long viewerUniversityId,
                                   Pageable pageable);

    /** Projection for the grouped count above. */
    interface TagCount {
        String getTag();
        long getTotal();
    }
}
