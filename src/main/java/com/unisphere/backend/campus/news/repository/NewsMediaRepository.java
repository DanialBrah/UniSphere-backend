package com.unisphere.backend.campus.news.repository;

import com.unisphere.backend.campus.news.entity.NewsMedia;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Media is cascaded through {@link com.unisphere.backend.campus.news.entity.NewsArticle}, so this
 * exists for ad-hoc lookups and tests rather than for the article write path.
 */
public interface NewsMediaRepository extends JpaRepository<NewsMedia, Long> {

    long countByArticleId(Long articleId);
}
