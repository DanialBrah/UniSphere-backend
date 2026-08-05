package com.unisphere.backend.campus.news.repository;

import com.unisphere.backend.campus.news.entity.NewsArticle;
import com.unisphere.backend.campus.news.enums.NewsCategory;
import com.unisphere.backend.campus.news.enums.NewsStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    // Explicit null check needed since @SQLRestriction filters soft-deleted rows,
    // but we need to fetch by ID for the delete operation before setting deleted_at
    @Query("SELECT a FROM NewsArticle a WHERE a.id = :id AND a.deletedAt IS NULL")
    Optional<NewsArticle> findActiveById(@Param("id") Long id);

    /**
     * The public feed, with optional category and featured facets. isAdmin/viewerUniversityId are
     * precomputed once per call in the service, so the visibility predicate stays a plain column
     * comparison and pagination totals remain exact.
     *
     * <p>No ORDER BY here on purpose: ordering comes from {@code @PageableDefault} on the
     * controller, so a query-embedded clause cannot silently end up merely a tiebreaker behind
     * Spring's appended one. {@code featured} is boxed so null means "no filter".
     */
    @Query("""
            SELECT a FROM NewsArticle a
            WHERE a.status = com.unisphere.backend.campus.news.enums.NewsStatus.PUBLISHED
              AND ( :isAdmin = true
                    OR a.visibility = com.unisphere.backend.campus.news.enums.NewsVisibility.PUBLIC
                    OR (a.visibility = com.unisphere.backend.campus.news.enums.NewsVisibility.UNIVERSITY
                        AND a.universityId = :viewerUniversityId) )
              AND (:category IS NULL OR a.category = :category)
              AND (:featured IS NULL OR a.featured = :featured)
            """)
    Page<NewsArticle> findFeed(@Param("isAdmin") boolean isAdmin,
                               @Param("viewerUniversityId") Long viewerUniversityId,
                               @Param("category") NewsCategory category,
                               @Param("featured") Boolean featured,
                               Pageable pageable);

    /**
     * Tag facet. Cannot fold into {@link #findFeed} — the tag match is a row-varying EXISTS, which
     * a precomputed parameter can't express.
     */
    @Query("""
            SELECT a FROM NewsArticle a
            WHERE a.status = com.unisphere.backend.campus.news.enums.NewsStatus.PUBLISHED
              AND EXISTS (SELECT 1 FROM NewsTag t WHERE t.article = a AND t.tag = :tag)
              AND ( :isAdmin = true
                    OR a.visibility = com.unisphere.backend.campus.news.enums.NewsVisibility.PUBLIC
                    OR (a.visibility = com.unisphere.backend.campus.news.enums.NewsVisibility.UNIVERSITY
                        AND a.universityId = :viewerUniversityId) )
              AND (:category IS NULL OR a.category = :category)
              AND (:featured IS NULL OR a.featured = :featured)
            """)
    Page<NewsArticle> findFeedByTag(@Param("tag") String tag,
                                    @Param("isAdmin") boolean isAdmin,
                                    @Param("viewerUniversityId") Long viewerUniversityId,
                                    @Param("category") NewsCategory category,
                                    @Param("featured") Boolean featured,
                                    Pageable pageable);

    /**
     * An author's articles. Drafts are included only for the author themselves or an admin;
     * everyone else sees published and archived ones subject to visibility.
     */
    @Query("""
            SELECT a FROM NewsArticle a
            WHERE a.authorId = :authorId
              AND ( :isOwnerOrAdmin = true
                    OR ( a.status <> com.unisphere.backend.campus.news.enums.NewsStatus.DRAFT
                         AND ( a.visibility = com.unisphere.backend.campus.news.enums.NewsVisibility.PUBLIC
                               OR (a.visibility = com.unisphere.backend.campus.news.enums.NewsVisibility.UNIVERSITY
                                   AND a.universityId = :viewerUniversityId) ) ) )
              AND (:status IS NULL OR a.status = :status)
            """)
    Page<NewsArticle> findByAuthorVisibleTo(@Param("authorId") Long authorId,
                                            @Param("isOwnerOrAdmin") boolean isOwnerOrAdmin,
                                            @Param("viewerUniversityId") Long viewerUniversityId,
                                            @Param("status") NewsStatus status,
                                            Pageable pageable);

    /**
     * FULLTEXT search. Native SQL bypasses @SQLRestriction, so deleted_at is filtered by hand —
     * same reason the posts equivalent does. status is pinned to PUBLISHED: search is discovery,
     * and drafts are not discoverable even by their own author.
     *
     * <p>Callers must strip any client-supplied sort before calling this — Spring appends the raw
     * property name to native SQL, and {@code ORDER BY publishedAt} is invalid against the
     * {@code published_at} column.
     */
    @Query(value = """
            SELECT * FROM news_articles a
            WHERE MATCH(a.title, a.content) AGAINST (:query IN BOOLEAN MODE)
              AND a.deleted_at IS NULL
              AND a.status = 'PUBLISHED'
              AND ( a.author_id = :viewerId
                    OR :isAdmin = true
                    OR a.visibility = 'PUBLIC'
                    OR (a.visibility = 'UNIVERSITY' AND a.university_id = :viewerUniversityId) )
            ORDER BY a.published_at DESC, a.id DESC
            """,
           countQuery = """
            SELECT COUNT(*) FROM news_articles a
            WHERE MATCH(a.title, a.content) AGAINST (:query IN BOOLEAN MODE)
              AND a.deleted_at IS NULL
              AND a.status = 'PUBLISHED'
              AND ( a.author_id = :viewerId
                    OR :isAdmin = true
                    OR a.visibility = 'PUBLIC'
                    OR (a.visibility = 'UNIVERSITY' AND a.university_id = :viewerUniversityId) )
            """,
           nativeQuery = true)
    Page<NewsArticle> searchFullText(@Param("query") String query,
                                     @Param("viewerId") Long viewerId,
                                     @Param("isAdmin") boolean isAdmin,
                                     @Param("viewerUniversityId") Long viewerUniversityId,
                                     Pageable pageable);

    /**
     * Flips every due scheduled article live in one statement.
     *
     * <p>A bulk UPDATE rather than select-then-save: the WHERE clause carries status = DRAFT, so a
     * second replica running the same tick matches zero rows instead of double-publishing — safe
     * on every instance with no distributed lock.
     *
     * <p>Two non-obvious details. @SQLRestriction is NOT applied to bulk HQL updates, so
     * {@code deletedAt IS NULL} is mandatory or a soft-deleted draft resurrects itself. And JPA
     * auditing does not fire on bulk updates, so updatedAt is set explicitly.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE NewsArticle a
               SET a.status      = com.unisphere.backend.campus.news.enums.NewsStatus.PUBLISHED,
                   a.publishedAt = a.scheduledAt,
                   a.scheduledAt = NULL,
                   a.updatedAt   = :now
             WHERE a.status = com.unisphere.backend.campus.news.enums.NewsStatus.DRAFT
               AND a.scheduledAt IS NOT NULL
               AND a.scheduledAt <= :now
               AND a.deletedAt IS NULL
            """)
    int publishDueArticles(@Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query("UPDATE NewsArticle a SET a.viewsCount = a.viewsCount + :delta WHERE a.id = :id")
    void incrementViewsCount(@Param("id") Long id, @Param("delta") long delta);

    @Modifying
    @Transactional
    @Query("UPDATE NewsArticle a SET a.likesCount = a.likesCount + :delta WHERE a.id = :id")
    void incrementLikesCount(@Param("id") Long id, @Param("delta") long delta);

    // Viewer and "whose likes we're listing" are always the same person (no "view someone else's
    // likes" endpoint exists), so one viewerId/isAdmin/viewerUniversityId set covers both roles.
    // Drafts are excluded outright: an article can be unpublished after you liked it.
    @Query(value = """
            SELECT a FROM NewsArticle a, NewsLike l
            WHERE l.articleId = a.id AND l.userId = :userId
              AND a.status <> com.unisphere.backend.campus.news.enums.NewsStatus.DRAFT
              AND ( a.authorId = :userId
                    OR :isAdmin = true
                    OR a.visibility = com.unisphere.backend.campus.news.enums.NewsVisibility.PUBLIC
                    OR (a.visibility = com.unisphere.backend.campus.news.enums.NewsVisibility.UNIVERSITY
                        AND a.universityId = :viewerUniversityId) )
            ORDER BY l.createdAt DESC
            """,
           countQuery = """
            SELECT COUNT(a) FROM NewsArticle a, NewsLike l
            WHERE l.articleId = a.id AND l.userId = :userId
              AND a.status <> com.unisphere.backend.campus.news.enums.NewsStatus.DRAFT
              AND ( a.authorId = :userId
                    OR :isAdmin = true
                    OR a.visibility = com.unisphere.backend.campus.news.enums.NewsVisibility.PUBLIC
                    OR (a.visibility = com.unisphere.backend.campus.news.enums.NewsVisibility.UNIVERSITY
                        AND a.universityId = :viewerUniversityId) )
            """)
    Page<NewsArticle> findLikedArticlesByUserId(@Param("userId") Long userId,
                                                @Param("isAdmin") boolean isAdmin,
                                                @Param("viewerUniversityId") Long viewerUniversityId,
                                                Pageable pageable);

    @Query(value = """
            SELECT a FROM NewsArticle a, NewsSave s
            WHERE s.articleId = a.id AND s.userId = :userId
              AND a.status <> com.unisphere.backend.campus.news.enums.NewsStatus.DRAFT
              AND ( a.authorId = :userId
                    OR :isAdmin = true
                    OR a.visibility = com.unisphere.backend.campus.news.enums.NewsVisibility.PUBLIC
                    OR (a.visibility = com.unisphere.backend.campus.news.enums.NewsVisibility.UNIVERSITY
                        AND a.universityId = :viewerUniversityId) )
            ORDER BY s.savedAt DESC
            """,
           countQuery = """
            SELECT COUNT(a) FROM NewsArticle a, NewsSave s
            WHERE s.articleId = a.id AND s.userId = :userId
              AND a.status <> com.unisphere.backend.campus.news.enums.NewsStatus.DRAFT
              AND ( a.authorId = :userId
                    OR :isAdmin = true
                    OR a.visibility = com.unisphere.backend.campus.news.enums.NewsVisibility.PUBLIC
                    OR (a.visibility = com.unisphere.backend.campus.news.enums.NewsVisibility.UNIVERSITY
                        AND a.universityId = :viewerUniversityId) )
            """)
    Page<NewsArticle> findSavedArticlesByUserId(@Param("userId") Long userId,
                                                @Param("isAdmin") boolean isAdmin,
                                                @Param("viewerUniversityId") Long viewerUniversityId,
                                                Pageable pageable);
}
