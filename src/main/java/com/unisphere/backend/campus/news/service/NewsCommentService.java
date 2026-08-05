package com.unisphere.backend.campus.news.service;

import com.unisphere.backend.campus.news.dto.request.CreateNewsCommentRequest;
import com.unisphere.backend.campus.news.dto.request.UpdateNewsCommentRequest;
import com.unisphere.backend.campus.news.dto.response.NewsAuthorResponse;
import com.unisphere.backend.campus.news.dto.response.NewsCommentResponse;
import com.unisphere.backend.campus.news.dto.response.NewsLikeToggleResponse;
import com.unisphere.backend.campus.news.entity.NewsArticle;
import com.unisphere.backend.campus.news.entity.NewsComment;
import com.unisphere.backend.campus.news.entity.NewsCommentLike;
import com.unisphere.backend.campus.news.enums.NewsStatus;
import com.unisphere.backend.campus.news.repository.NewsCommentLikeRepository;
import com.unisphere.backend.campus.news.repository.NewsCommentRepository;
import com.unisphere.backend.common.exception.NewsCommentNotFoundException;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class NewsCommentService {

    static final String REDIS_NEWS_COMMENT_LIKES = "news:comment:likes:";

    private final NewsCommentRepository newsCommentRepository;
    private final NewsCommentLikeRepository newsCommentLikeRepository;
    private final UserRepository userRepository;
    private final MediaUrlResolver mediaUrlResolver;
    private final RedisTemplate<String, Long> redisTemplate;
    private final NotificationService notificationService;
    private final NewsService newsService;

    public NewsCommentResponse createComment(Long articleId, CreateNewsCommentRequest req, User currentUser) {
        NewsArticle article = newsService.findViewableArticle(articleId, currentUser);
        assertCommentable(article);

        NewsComment comment = new NewsComment();
        comment.setArticleId(articleId);
        comment.setUserId(currentUser.getId());
        comment.setContent(req.content());
        comment.setParentCommentId(req.parentCommentId());

        newsCommentRepository.save(comment);

        notificationService.createAndPush(article.getAuthorId(), currentUser.getId(),
                NotificationType.COMMENT, articleId, "NEWS_ARTICLE");

        // On a reply, also notify the parent comment's author (unless that is the article author,
        // who was just notified above).
        if (req.parentCommentId() != null) {
            newsCommentRepository.findById(req.parentCommentId()).ifPresent(parent -> {
                if (parent.getArticleId().equals(articleId)
                        && !parent.getUserId().equals(article.getAuthorId())) {
                    notificationService.createAndPush(parent.getUserId(), currentUser.getId(),
                            NotificationType.COMMENT, articleId, "NEWS_ARTICLE");
                }
            });
        }

        return toCommentResponse(comment, currentUser);
    }

    @Transactional(readOnly = true)
    public Page<NewsCommentResponse> getTopLevelComments(Long articleId, Pageable pageable, User currentUser) {
        newsService.findViewableArticle(articleId, currentUser);
        return toCommentResponses(
                newsCommentRepository.findByArticleIdAndParentCommentIdIsNullOrderByCreatedAtAsc(
                        articleId, pageable),
                currentUser);
    }

    @Transactional(readOnly = true)
    public Page<NewsCommentResponse> getReplies(Long commentId, Pageable pageable, User currentUser) {
        NewsComment parent = findActiveComment(commentId);
        assertCanViewParentArticle(parent, currentUser);
        return toCommentResponses(
                newsCommentRepository.findByParentCommentIdOrderByCreatedAtAsc(commentId, pageable),
                currentUser);
    }

    public NewsCommentResponse updateComment(Long commentId, UpdateNewsCommentRequest req, User currentUser) {
        NewsComment comment = findActiveComment(commentId);
        assertCanViewParentArticle(comment, currentUser);
        if (!comment.getUserId().equals(currentUser.getId())) {
            throw new UnauthorizedActionException("You do not own this comment");
        }
        comment.setContent(req.content());
        return toCommentResponse(newsCommentRepository.save(comment), currentUser);
    }

    public void deleteComment(Long commentId, User currentUser) {
        NewsComment comment = findActiveComment(commentId);
        assertCanViewParentArticle(comment, currentUser);
        if (!comment.getUserId().equals(currentUser.getId()) && currentUser.getRole() != Role.ADMIN) {
            throw new UnauthorizedActionException("You cannot delete this comment");
        }
        comment.setDeletedAt(LocalDateTime.now());
        newsCommentRepository.save(comment);
    }

    public NewsLikeToggleResponse toggleLike(Long commentId, User currentUser) {
        NewsComment comment = findActiveComment(commentId);
        assertCanViewParentArticle(comment, currentUser);
        Long userId = currentUser.getId();

        if (newsCommentLikeRepository.existsByCommentIdAndUserId(commentId, userId)) {
            newsCommentLikeRepository.deleteByCommentIdAndUserId(commentId, userId);
            redisIncrement(REDIS_NEWS_COMMENT_LIKES + commentId, -1);
            return new NewsLikeToggleResponse(false, newsCommentLikeRepository.countByCommentId(commentId));
        }
        newsCommentLikeRepository.save(new NewsCommentLike(commentId, userId));
        redisIncrement(REDIS_NEWS_COMMENT_LIKES + commentId, 1);
        notificationService.createAndPush(comment.getUserId(), userId,
                NotificationType.LIKE, comment.getArticleId(), "NEWS_ARTICLE");
        return new NewsLikeToggleResponse(true, newsCommentLikeRepository.countByCommentId(commentId));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private NewsComment findActiveComment(Long commentId) {
        return newsCommentRepository.findActiveById(commentId)
                .orElseThrow(() -> new NewsCommentNotFoundException(commentId));
    }

    /**
     * Comments never carry their own visibility — they inherit the article's. Resolving through
     * NewsComment.articleId (not a caller-supplied path variable) matters because the controller
     * does not validate that a path's articleId actually owns the given commentId.
     */
    private void assertCanViewParentArticle(NewsComment comment, User currentUser) {
        newsService.findViewableArticle(comment.getArticleId(), currentUser);
    }

    /**
     * Discussion opens with publication and closes with archival: a draft is uncommentable even by
     * its own author, and an archived article stays readable but read-only.
     */
    private void assertCommentable(NewsArticle article) {
        if (article.getStatus() != NewsStatus.PUBLISHED) {
            throw new IllegalArgumentException("Comments are only open on published articles");
        }
    }

    private Page<NewsCommentResponse> toCommentResponses(Page<NewsComment> comments, User currentUser) {
        PageContext ctx = loadPageContext(comments.getContent(), currentUser);
        return comments.map(c -> toCommentResponse(c, ctx));
    }

    private record PageContext(Map<Long, User> authors, Set<Long> likedCommentIds,
                               Map<Long, Long> replyCounts) {}

    private PageContext loadPageContext(List<NewsComment> comments, User currentUser) {
        if (comments.isEmpty()) return new PageContext(Map.of(), Set.of(), Map.of());

        Set<Long> commentIds = comments.stream().map(NewsComment::getId).collect(Collectors.toSet());
        Set<Long> authorIds  = comments.stream().map(NewsComment::getUserId).collect(Collectors.toSet());

        Map<Long, Long> replyCounts = newsCommentRepository.countRepliesByParentIds(commentIds).stream()
                .collect(Collectors.toMap(NewsCommentRepository.CountByKey::getId,
                                          NewsCommentRepository.CountByKey::getTotal));

        return new PageContext(
                userRepository.findAllById(authorIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u)),
                newsCommentLikeRepository.findLikedCommentIds(currentUser.getId(), commentIds),
                replyCounts);
    }

    private NewsCommentResponse toCommentResponse(NewsComment comment, User currentUser) {
        return toCommentResponse(comment, loadPageContext(List.of(comment), currentUser));
    }

    private NewsCommentResponse toCommentResponse(NewsComment comment, PageContext ctx) {
        long likesCount = comment.getLikesCount()
                + redisGetDelta(REDIS_NEWS_COMMENT_LIKES + comment.getId());

        return new NewsCommentResponse(
                comment.getId(), comment.getArticleId(), comment.getParentCommentId(),
                resolveAuthor(comment.getUserId(), ctx.authors()),
                comment.getContent(), likesCount,
                ctx.replyCounts().getOrDefault(comment.getId(), 0L),
                ctx.likedCommentIds().contains(comment.getId()),
                comment.getCreatedAt(), comment.getUpdatedAt());
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
