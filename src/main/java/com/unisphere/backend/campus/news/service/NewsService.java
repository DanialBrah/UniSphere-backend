package com.unisphere.backend.campus.news.service;

import com.unisphere.backend.campus.news.dto.request.CreateNewsArticleRequest;
import com.unisphere.backend.campus.news.dto.request.NewsStatusUpdateRequest;
import com.unisphere.backend.campus.news.dto.request.UpdateNewsArticleRequest;
import com.unisphere.backend.campus.news.dto.response.*;
import com.unisphere.backend.campus.news.entity.NewsArticle;
import com.unisphere.backend.campus.news.entity.NewsLike;
import com.unisphere.backend.campus.news.entity.NewsMedia;
import com.unisphere.backend.campus.news.entity.NewsSave;
import com.unisphere.backend.campus.news.entity.NewsTag;
import com.unisphere.backend.campus.news.enums.NewsCategory;
import com.unisphere.backend.campus.news.enums.NewsMediaType;
import com.unisphere.backend.campus.news.enums.NewsStatus;
import com.unisphere.backend.campus.news.enums.NewsVisibility;
import com.unisphere.backend.campus.news.mapper.NewsMapper;
import com.unisphere.backend.campus.news.repository.*;
import com.unisphere.backend.common.exception.InvalidNewsStatusTransitionException;
import com.unisphere.backend.common.exception.NewsArticleNotFoundException;
import com.unisphere.backend.common.exception.UnauthorizedActionException;
import com.unisphere.backend.common.storage.MediaUrlResolver;
import com.unisphere.backend.identity.entity.Role;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.repository.UserRepository;
import com.unisphere.backend.identity.service.UserService;
import com.unisphere.backend.social.notification.enums.NotificationType;
import com.unisphere.backend.social.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class NewsService {

    static final String REDIS_NEWS_VIEWS = "news:views:";
    static final String REDIS_NEWS_LIKES = "news:likes:";

    /**
     * FULLTEXT BOOLEAN MODE treats these as operators; an unbalanced one raises a MySQL syntax
     * error, which would surface as a 500 on a plain user search.
     */
    private static final Pattern FULLTEXT_OPERATORS = Pattern.compile("[+\\-><()~*\"@]");

    private final NewsArticleRepository newsArticleRepository;
    private final NewsCommentRepository newsCommentRepository;
    private final NewsLikeRepository newsLikeRepository;
    private final NewsSaveRepository newsSaveRepository;
    private final NewsTagRepository newsTagRepository;
    private final UserRepository userRepository;
    private final NewsMapper newsMapper;
    private final MediaUrlResolver mediaUrlResolver;
    private final RedisTemplate<String, Long> redisTemplate;
    private final NotificationService notificationService;
    private final NewsAccessService newsAccessService;
    private final NewsMediaService newsMediaService;

    // ── Writes ───────────────────────────────────────────────────────────────

    public NewsArticleResponse createArticle(CreateNewsArticleRequest req, User currentUser) {
        newsAccessService.assertCanAuthor(currentUser);

        NewsStatus status = req.status() != null ? req.status() : NewsStatus.DRAFT;
        if (status == NewsStatus.ARCHIVED) {
            throw new InvalidNewsStatusTransitionException(NewsStatus.DRAFT, NewsStatus.ARCHIVED);
        }

        NewsVisibility visibility = req.visibility() != null ? req.visibility() : NewsVisibility.PUBLIC;

        NewsArticle article = new NewsArticle();
        article.setAuthorId(currentUser.getId());
        article.setTitle(req.title());
        article.setSummary(req.summary());
        article.setContent(req.content() != null ? req.content() : "");
        article.setCategory(req.category() != null ? req.category() : NewsCategory.GENERAL);
        article.setVisibility(visibility);
        // Never trust a client-supplied universityId — there is no such request field on purpose.
        article.setUniversityId(newsAccessService.resolveArticleUniversityId(currentUser, visibility));

        if (req.coverImageKey() != null && !req.coverImageKey().isBlank()) {
            newsMediaService.assertOwnedKey(req.coverImageKey(), currentUser);
            article.setCoverImageKey(req.coverImageKey());
        }

        if (req.media() != null) {
            int order = 0;
            for (CreateNewsArticleRequest.MediaItem item : req.media()) {
                newsMediaService.assertOwnedKey(item.mediaKey(), currentUser);
                article.getMedia().add(newMedia(article, item.mediaKey(), item.mediaType(), order++));
            }
        }

        if (req.tags() != null) {
            replaceTags(article, req.tags());
        }

        if (status == NewsStatus.PUBLISHED) {
            assertPublishable(article);
            article.setStatus(NewsStatus.PUBLISHED);
            article.setPublishedAt(LocalDateTime.now());
        } else {
            article.setStatus(NewsStatus.DRAFT);
            article.setScheduledAt(req.scheduledAt());
        }

        newsArticleRepository.save(article);
        return toArticleResponse(article, currentUser);
    }

    public NewsArticleResponse updateArticle(Long articleId, UpdateNewsArticleRequest req, User currentUser) {
        NewsArticle article = findViewableArticle(articleId, currentUser);
        assertCanModify(article, currentUser);

        if (req.title() != null)   article.setTitle(req.title());
        if (req.summary() != null) article.setSummary(req.summary());
        if (req.content() != null) article.setContent(req.content());
        if (req.category() != null) article.setCategory(req.category());

        if (req.visibility() != null) {
            article.setVisibility(req.visibility());
            // Re-derive rather than keep the old value: switching PUBLIC -> UNIVERSITY on an
            // article whose universityId is still null would make it invisible to everyone.
            article.setUniversityId(newsAccessService.resolveArticleUniversityId(
                    resolveAuthorUser(article), req.visibility()));
        }

        if (req.coverImageKey() != null) {
            if (req.coverImageKey().isBlank()) {
                article.setCoverImageKey(null);
            } else {
                newsMediaService.assertOwnedKey(req.coverImageKey(), currentUser);
                article.setCoverImageKey(req.coverImageKey());
            }
        }

        if (req.tags() != null) {
            replaceTags(article, req.tags());
        }

        if (req.removeMediaIds() != null && !req.removeMediaIds().isEmpty()) {
            article.getMedia().removeIf(m -> req.removeMediaIds().contains(m.getId()));
        }

        if (req.addMedia() != null && !req.addMedia().isEmpty()) {
            int nextOrder = article.getMedia().stream()
                    .mapToInt(NewsMedia::getSortOrder).max().orElse(-1) + 1;
            for (UpdateNewsArticleRequest.MediaItem item : req.addMedia()) {
                newsMediaService.assertOwnedKey(item.mediaKey(), currentUser);
                article.getMedia().add(newMedia(article, item.mediaKey(), item.mediaType(), nextOrder++));
            }
        }

        if (article.getStatus() == NewsStatus.PUBLISHED) {
            assertPublishable(article);
        }

        return toArticleResponse(newsArticleRepository.save(article), currentUser);
    }

    /**
     * The whole status machine, in one place — which is why UpdateNewsArticleRequest has no
     * status field.
     */
    public NewsArticleResponse changeStatus(Long articleId, NewsStatusUpdateRequest req, User currentUser) {
        NewsArticle article = findViewableArticle(articleId, currentUser);
        assertCanModify(article, currentUser);

        NewsStatus from = article.getStatus();
        NewsStatus to = req.status();

        switch (from) {
            case DRAFT -> {
                if (to == NewsStatus.ARCHIVED) {
                    // Nothing to archive — the article was never public.
                    throw new InvalidNewsStatusTransitionException(from, to);
                }
                if (to == NewsStatus.PUBLISHED) {
                    assertPublishable(article);
                    article.setStatus(NewsStatus.PUBLISHED);
                    article.setPublishedAt(LocalDateTime.now());
                    article.setScheduledAt(null);
                } else {
                    // DRAFT -> DRAFT: schedule, reschedule, or (with a null) unschedule.
                    article.setScheduledAt(req.scheduledAt());
                }
            }
            case PUBLISHED -> {
                if (to == NewsStatus.ARCHIVED) {
                    article.setStatus(NewsStatus.ARCHIVED);
                } else if (to == NewsStatus.DRAFT) {
                    // Unpublish. publishedAt is retained for audit; scheduledAt may re-queue it.
                    article.setStatus(NewsStatus.DRAFT);
                    article.setScheduledAt(req.scheduledAt());
                }
                // PUBLISHED -> PUBLISHED is a no-op.
            }
            case ARCHIVED -> {
                if (to == NewsStatus.DRAFT) {
                    throw new InvalidNewsStatusTransitionException(from, to);
                }
                if (to == NewsStatus.PUBLISHED) {
                    assertPublishable(article);
                    article.setStatus(NewsStatus.PUBLISHED);
                    // Keep the original publishedAt — restoring is not republishing.
                    if (article.getPublishedAt() == null) {
                        article.setPublishedAt(LocalDateTime.now());
                    }
                }
                // ARCHIVED -> ARCHIVED is a no-op.
            }
        }

        return toArticleResponse(newsArticleRepository.save(article), currentUser);
    }

    public void deleteArticle(Long articleId, User currentUser) {
        NewsArticle article = findViewableArticle(articleId, currentUser);
        assertCanModify(article, currentUser);
        article.setDeletedAt(LocalDateTime.now());
        newsArticleRepository.save(article);
    }

    // ── Reads ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public NewsArticleResponse getArticleById(Long articleId, User currentUser) {
        NewsArticle article = findViewableArticle(articleId, currentUser);
        // Authors reading their own work don't inflate an editorially meaningful counter.
        if (!article.getAuthorId().equals(currentUser.getId())) {
            redisIncrement(REDIS_NEWS_VIEWS + articleId, 1);
        }
        return toArticleResponse(article, currentUser);
    }

    @Transactional(readOnly = true)
    public Page<NewsArticleSummaryResponse> getFeed(NewsCategory category, String tag, Boolean featured,
                                                    Pageable pageable, User currentUser) {
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        Long viewerUniversityId = newsAccessService.viewerUniversityId(currentUser);

        Page<NewsArticle> page = (tag == null || tag.isBlank())
                ? newsArticleRepository.findFeed(isAdmin, viewerUniversityId, category, featured, pageable)
                : newsArticleRepository.findFeedByTag(tag.trim(), isAdmin, viewerUniversityId,
                                                      category, featured, pageable);
        return toSummaryResponses(page, currentUser);
    }

    @Transactional(readOnly = true)
    public Page<NewsArticleSummaryResponse> search(String query, Pageable pageable, User currentUser) {
        String sanitized = sanitizeBooleanQuery(query);
        if (sanitized.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        // The repository query is native and already carries its own ORDER BY. Spring appends any
        // Pageable sort as a raw property name, so `?sort=publishedAt` would emit invalid SQL
        // against the published_at column — strip it before it can reach the query.
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        return toSummaryResponses(
                newsArticleRepository.searchFullText(sanitized, currentUser.getId(), isAdmin,
                        newsAccessService.viewerUniversityId(currentUser), unsorted),
                currentUser);
    }

    @Transactional(readOnly = true)
    public Page<NewsArticleSummaryResponse> getArticlesByAuthor(Long authorId, NewsStatus status,
                                                                Pageable pageable, User currentUser) {
        boolean isOwnerOrAdmin = authorId.equals(currentUser.getId()) || currentUser.getRole() == Role.ADMIN;
        return toSummaryResponses(
                newsArticleRepository.findByAuthorVisibleTo(authorId, isOwnerOrAdmin,
                        newsAccessService.viewerUniversityId(currentUser), status, pageable),
                currentUser);
    }

    @Transactional(readOnly = true)
    public Page<NewsArticleSummaryResponse> getMyArticles(NewsStatus status, Pageable pageable, User currentUser) {
        return toSummaryResponses(
                newsArticleRepository.findByAuthorVisibleTo(currentUser.getId(), true,
                        newsAccessService.viewerUniversityId(currentUser), status, pageable),
                currentUser);
    }

    @Transactional(readOnly = true)
    public Page<NewsArticleSummaryResponse> getLikedArticles(Pageable pageable, User currentUser) {
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        return toSummaryResponses(
                newsArticleRepository.findLikedArticlesByUserId(currentUser.getId(), isAdmin,
                        newsAccessService.viewerUniversityId(currentUser), pageable),
                currentUser);
    }

    @Transactional(readOnly = true)
    public Page<NewsArticleSummaryResponse> getSavedArticles(Pageable pageable, User currentUser) {
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        return toSummaryResponses(
                newsArticleRepository.findSavedArticlesByUserId(currentUser.getId(), isAdmin,
                        newsAccessService.viewerUniversityId(currentUser), pageable),
                currentUser);
    }

    @Transactional(readOnly = true)
    public List<NewsTagCountResponse> getPopularTags(int limit, User currentUser) {
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        return newsTagRepository.findPopularTags(isAdmin,
                        newsAccessService.viewerUniversityId(currentUser),
                        PageRequest.of(0, Math.clamp(limit, 1, 100)))
                .stream()
                .map(t -> new NewsTagCountResponse(t.getTag(), t.getTotal()))
                .toList();
    }

    // ── Engagement ───────────────────────────────────────────────────────────

    public NewsLikeToggleResponse toggleLike(Long articleId, User currentUser) {
        NewsArticle article = findViewableArticle(articleId, currentUser);
        Long userId = currentUser.getId();

        if (newsLikeRepository.existsByArticleIdAndUserId(articleId, userId)) {
            newsLikeRepository.deleteByArticleIdAndUserId(articleId, userId);
            redisIncrement(REDIS_NEWS_LIKES + articleId, -1);
            return new NewsLikeToggleResponse(false, newsLikeRepository.countByArticleId(articleId));
        }
        newsLikeRepository.save(new NewsLike(articleId, userId));
        redisIncrement(REDIS_NEWS_LIKES + articleId, 1);
        notificationService.createAndPush(article.getAuthorId(), userId,
                NotificationType.LIKE, articleId, "NEWS_ARTICLE");
        return new NewsLikeToggleResponse(true, newsLikeRepository.countByArticleId(articleId));
    }

    public NewsSaveToggleResponse toggleSave(Long articleId, User currentUser) {
        findViewableArticle(articleId, currentUser);
        Long userId = currentUser.getId();

        if (newsSaveRepository.existsByUserIdAndArticleId(userId, articleId)) {
            newsSaveRepository.deleteByUserIdAndArticleId(userId, articleId);
            return new NewsSaveToggleResponse(false);
        }
        newsSaveRepository.save(new NewsSave(userId, articleId));
        return new NewsSaveToggleResponse(true);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Package-private so NewsCommentService can resolve an article under the same 404-on-invisible
     * rule rather than duplicating it.
     */
    NewsArticle findViewableArticle(Long articleId, User currentUser) {
        NewsArticle article = newsArticleRepository.findActiveById(articleId)
                .orElseThrow(() -> new NewsArticleNotFoundException(articleId));
        if (!newsAccessService.canView(article, currentUser)) {
            // 404, not 403 — a 403 would confirm the article exists.
            throw new NewsArticleNotFoundException(articleId);
        }
        return article;
    }

    /** The author owns their article; an ADMIN may act on any. */
    private void assertCanModify(NewsArticle article, User currentUser) {
        if (!article.getAuthorId().equals(currentUser.getId()) && currentUser.getRole() != Role.ADMIN) {
            throw new UnauthorizedActionException("You cannot modify this article");
        }
    }

    private void assertPublishable(NewsArticle article) {
        if (article.getTitle() == null || article.getTitle().isBlank()) {
            throw new IllegalArgumentException("A published article must have a title");
        }
        if (article.getContent() == null || article.getContent().isBlank()) {
            throw new IllegalArgumentException("A published article must have content");
        }
    }

    private User resolveAuthorUser(NewsArticle article) {
        return userRepository.findById(article.getAuthorId())
                .orElseThrow(() -> new NewsArticleNotFoundException(article.getId()));
    }

    private NewsMedia newMedia(NewsArticle article, String mediaKey, String rawType, int sortOrder) {
        NewsMedia media = new NewsMedia();
        media.setArticle(article);
        media.setMediaKey(mediaKey);
        media.setMediaType(resolveMediaType(rawType));
        media.setSortOrder(sortOrder);
        return media;
    }

    /**
     * Reconciles the tag set to exactly {@code tags} (trimmed, de-duplicated — the
     * (article_id, tag) unique constraint rejects duplicates outright).
     *
     * <p>A diff rather than clear-then-re-add: with orphanRemoval Hibernate orders INSERTs before
     * DELETEs within a flush, so re-adding a tag that is already present would hit the unique
     * constraint before the old row is removed. Touching only what actually changed sidesteps the
     * ordering entirely.
     */
    private void replaceTags(NewsArticle article, List<String> tags) {
        List<String> desired = tags.stream()
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .distinct()
                .toList();

        article.getTags().removeIf(existing -> !desired.contains(existing.getTag()));

        Set<String> kept = article.getTags().stream()
                .map(NewsTag::getTag)
                .collect(Collectors.toSet());

        desired.stream().filter(t -> !kept.contains(t)).forEach(t -> {
            NewsTag tag = new NewsTag();
            tag.setArticle(article);
            tag.setTag(t);
            article.getTags().add(tag);
        });
    }

    private NewsMediaType resolveMediaType(String rawType) {
        if (rawType == null) return NewsMediaType.IMAGE;
        return rawType.toLowerCase().startsWith("video") ? NewsMediaType.VIDEO : NewsMediaType.IMAGE;
    }

    private String sanitizeBooleanQuery(String raw) {
        if (raw == null) return "";
        return FULLTEXT_OPERATORS.matcher(raw).replaceAll(" ").trim();
    }

    // ── Response mapping ─────────────────────────────────────────────────────

    /**
     * Page-level mapping: authors, the viewer's like/save flags and comment counts are batch-loaded
     * once for the whole page. Mapping article-by-article would issue those lookups per row, so a
     * 20-article feed would cost 80 extra round-trips.
     */
    private Page<NewsArticleSummaryResponse> toSummaryResponses(Page<NewsArticle> articles, User currentUser) {
        PageContext ctx = loadPageContext(articles.getContent(), currentUser);
        return articles.map(a -> toSummaryResponse(a, ctx));
    }

    private record PageContext(Map<Long, User> authors, Set<Long> likedArticleIds,
                               Set<Long> savedArticleIds, Map<Long, Long> commentCounts) {}

    private PageContext loadPageContext(List<NewsArticle> articles, User currentUser) {
        if (articles.isEmpty()) return new PageContext(Map.of(), Set.of(), Set.of(), Map.of());

        Set<Long> articleIds = articles.stream().map(NewsArticle::getId).collect(Collectors.toSet());
        Set<Long> authorIds = articles.stream().map(NewsArticle::getAuthorId).collect(Collectors.toSet());
        Long viewerId = currentUser.getId();

        Map<Long, Long> commentCounts = newsCommentRepository.countTopLevelByArticleIds(articleIds).stream()
                .collect(Collectors.toMap(NewsCommentRepository.CountByKey::getId,
                                          NewsCommentRepository.CountByKey::getTotal));

        return new PageContext(
                userRepository.findAllById(authorIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u)),
                newsLikeRepository.findLikedArticleIds(viewerId, articleIds),
                newsSaveRepository.findSavedArticleIds(viewerId, articleIds),
                commentCounts);
    }

    NewsArticleResponse toArticleResponse(NewsArticle article, User currentUser) {
        PageContext ctx = loadPageContext(List.of(article), currentUser);

        // Presigning happens in NewsMapper — every article, not just restricted ones, because a
        // plain public URL only resolves on GCS (Garage has no anonymous access, B2 is private).
        List<NewsMediaResponse> media = article.getMedia().stream()
                .map(newsMapper::toMediaResponse)
                .toList();

        return new NewsArticleResponse(
                article.getId(), resolveAuthor(article.getAuthorId(), ctx.authors()),
                article.getTitle(), article.getSummary(), article.getContent(),
                mediaUrlResolver.toViewableUrl(article.getCoverImageKey()),
                article.getCategory(), article.getStatus(), article.getVisibility(),
                article.getUniversityId(), article.isFeatured(),
                effectiveViews(article), effectiveLikes(article),
                ctx.commentCounts().getOrDefault(article.getId(), 0L),
                ctx.likedArticleIds().contains(article.getId()),
                ctx.savedArticleIds().contains(article.getId()),
                tagNames(article), media,
                article.getPublishedAt(), article.getScheduledAt(),
                article.getCreatedAt(), article.getUpdatedAt());
    }

    private NewsArticleSummaryResponse toSummaryResponse(NewsArticle article, PageContext ctx) {
        return new NewsArticleSummaryResponse(
                article.getId(), resolveAuthor(article.getAuthorId(), ctx.authors()),
                article.getTitle(), article.getSummary(),
                mediaUrlResolver.toViewableUrl(article.getCoverImageKey()),
                article.getCategory(), article.getStatus(), article.getVisibility(),
                article.getUniversityId(), article.isFeatured(),
                effectiveViews(article), effectiveLikes(article),
                ctx.commentCounts().getOrDefault(article.getId(), 0L),
                ctx.likedArticleIds().contains(article.getId()),
                ctx.savedArticleIds().contains(article.getId()),
                tagNames(article),
                article.getPublishedAt(), article.getCreatedAt());
    }

    private List<String> tagNames(NewsArticle article) {
        return article.getTags().stream().map(NewsTag::getTag).toList();
    }

    private long effectiveViews(NewsArticle article) {
        return article.getViewsCount() + redisGetDelta(REDIS_NEWS_VIEWS + article.getId());
    }

    private long effectiveLikes(NewsArticle article) {
        return article.getLikesCount() + redisGetDelta(REDIS_NEWS_LIKES + article.getId());
    }

    private NewsAuthorResponse resolveAuthor(Long userId, Map<Long, User> authors) {
        User author = authors.get(userId);
        if (author == null) return new NewsAuthorResponse(userId, "Unknown", null, "UNKNOWN");
        // avatar_url holds a bare key since changeset 012 — it has to be signed to be fetchable.
        return new NewsAuthorResponse(author.getId(), UserService.resolveDisplayName(author),
                mediaUrlResolver.toViewableUrl(author.getAvatarUrl()), author.getRole().name());
    }

    private long redisGetDelta(String key) {
        try {
            Long v = redisTemplate.opsForValue().get(key);
            return v != null ? v : 0L;
        } catch (Exception ex) {
            log.debug("Redis unavailable, skipping delta for {}: {}", key, ex.getMessage());
            return 0L;
        }
    }

    private void redisIncrement(String key, long delta) {
        try {
            if (delta >= 0) redisTemplate.opsForValue().increment(key, delta);
            else            redisTemplate.opsForValue().decrement(key, -delta);
        } catch (Exception ex) {
            log.warn("Redis unavailable, counter not updated for {}: {}", key, ex.getMessage());
        }
    }
}
