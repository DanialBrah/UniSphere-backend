package com.unisphere.backend.social.posting.service;

import com.unisphere.backend.common.exception.CommentNotFoundException;
import com.unisphere.backend.common.storage.MediaUrlResolver;
import com.unisphere.backend.common.exception.PostNotFoundException;
import com.unisphere.backend.common.exception.UnauthorizedActionException;
import com.unisphere.backend.identity.entity.Role;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.repository.UserRepository;
import com.unisphere.backend.social.posting.dto.request.CreateCommentRequest;
import com.unisphere.backend.social.posting.dto.request.UpdateCommentRequest;
import com.unisphere.backend.social.posting.dto.response.CommentResponse;
import com.unisphere.backend.social.posting.dto.response.LikeToggleResponse;
import com.unisphere.backend.social.posting.dto.response.PostAuthorResponse;
import com.unisphere.backend.social.posting.entity.Comment;
import com.unisphere.backend.social.posting.entity.CommentLike;
import com.unisphere.backend.social.posting.mapper.CommentMapper;
import com.unisphere.backend.social.notification.enums.NotificationType;
import com.unisphere.backend.social.notification.service.NotificationService;
import com.unisphere.backend.social.posting.repository.CommentLikeRepository;
import com.unisphere.backend.social.posting.repository.CommentRepository;
import com.unisphere.backend.social.posting.repository.PostRepository;
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
public class CommentService {

    private static final String REDIS_COMMENT_LIKES = "comment:likes:";

    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentMapper commentMapper;
    private final MediaUrlResolver mediaUrlResolver;
    private final RedisTemplate<String, Long> redisTemplate;
    private final NotificationService notificationService;
    private final PostAccessService postAccessService;

    public CommentResponse createComment(Long postId, CreateCommentRequest req, User currentUser) {
        var post = postRepository.findActiveById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        if (!postAccessService.canView(post, currentUser)) {
            throw new PostNotFoundException(postId);
        }

        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setUserId(currentUser.getId());
        comment.setContent(req.content());
        comment.setParentCommentId(req.parentCommentId());

        commentRepository.save(comment);

        // Notify the post owner about the new comment
        notificationService.createAndPush(post.getUserId(), currentUser.getId(), NotificationType.COMMENT, postId, "POST");

        // If this is a reply, also notify the parent comment's author
        if (req.parentCommentId() != null) {
            commentRepository.findById(req.parentCommentId()).ifPresent(parent -> {
                if (parent.getPostId().equals(postId) && !parent.getUserId().equals(post.getUserId())) {
                    notificationService.createAndPush(parent.getUserId(), currentUser.getId(), NotificationType.COMMENT, postId, "POST");
                }
            });
        }

        return toCommentResponse(comment, currentUser);
    }

    @Transactional(readOnly = true)
    public Page<CommentResponse> getTopLevelComments(Long postId, Pageable pageable, User currentUser) {
        var post = postRepository.findActiveById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        if (!postAccessService.canView(post, currentUser)) {
            throw new PostNotFoundException(postId);
        }
        return toCommentResponses(
                commentRepository.findByPostIdAndParentCommentIdIsNullOrderByCreatedAtAsc(postId, pageable),
                currentUser);
    }

    @Transactional(readOnly = true)
    public Page<CommentResponse> getReplies(Long commentId, Pageable pageable, User currentUser) {
        Comment parent = findActiveComment(commentId);
        assertCanViewParentPost(parent, currentUser);
        return toCommentResponses(
                commentRepository.findByParentCommentIdOrderByCreatedAtAsc(commentId, pageable),
                currentUser);
    }

    public CommentResponse updateComment(Long commentId, UpdateCommentRequest req, User currentUser) {
        Comment comment = findActiveComment(commentId);
        assertCanViewParentPost(comment, currentUser);
        assertOwner(comment.getUserId(), currentUser);
        comment.setContent(req.content());
        return toCommentResponse(commentRepository.save(comment), currentUser);
    }

    public void deleteComment(Long commentId, User currentUser) {
        Comment comment = findActiveComment(commentId);
        assertCanViewParentPost(comment, currentUser);
        if (!comment.getUserId().equals(currentUser.getId()) && currentUser.getRole() != Role.ADMIN) {
            throw new UnauthorizedActionException("You cannot delete this comment");
        }
        comment.setDeletedAt(LocalDateTime.now());
        commentRepository.save(comment);
    }

    public LikeToggleResponse toggleLike(Long commentId, User currentUser) {
        Comment comment = findActiveComment(commentId);
        assertCanViewParentPost(comment, currentUser);
        Long userId = currentUser.getId();

        if (commentLikeRepository.existsByCommentIdAndUserId(commentId, userId)) {
            commentLikeRepository.deleteByCommentIdAndUserId(commentId, userId);
            redisIncrement(REDIS_COMMENT_LIKES + commentId, -1);
            return new LikeToggleResponse(false, effectiveLikeCount(commentId));
        } else {
            commentLikeRepository.save(new CommentLike(commentId, userId));
            redisIncrement(REDIS_COMMENT_LIKES + commentId, 1);
            return new LikeToggleResponse(true, effectiveLikeCount(commentId));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Comment findActiveComment(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));
    }

    // Comments never carry their own visibility — they inherit the parent post's. Resolving
    // through Comment.postId (not a caller-supplied postId) matters because the controller doesn't
    // validate that a path's postId actually owns the given commentId.
    private void assertCanViewParentPost(Comment comment, User currentUser) {
        var post = postRepository.findActiveById(comment.getPostId())
                .orElseThrow(() -> new PostNotFoundException(comment.getPostId()));
        if (!postAccessService.canView(post, currentUser)) {
            throw new PostNotFoundException(comment.getPostId());
        }
    }

    private void assertOwner(Long ownerId, User currentUser) {
        if (!ownerId.equals(currentUser.getId())) {
            throw new UnauthorizedActionException("You do not own this resource");
        }
    }

    private long effectiveLikeCount(Long commentId) {
        return commentLikeRepository.countByCommentId(commentId);
    }

    /**
     * Page-level mapping: authors, like flags and reply counts are fetched once for the whole page
     * rather than once per comment.
     */
    private Page<CommentResponse> toCommentResponses(Page<Comment> comments, User currentUser) {
        PageContext ctx = loadPageContext(comments.getContent(), currentUser);
        return comments.map(c -> toCommentResponse(c, currentUser, ctx));
    }

    private record PageContext(Map<Long, User> authors, Set<Long> likedCommentIds,
                               Map<Long, Long> replyCounts) {}

    private PageContext loadPageContext(List<Comment> comments, User currentUser) {
        if (comments.isEmpty()) return new PageContext(Map.of(), Set.of(), Map.of());

        Set<Long> commentIds = comments.stream().map(Comment::getId).collect(Collectors.toSet());
        Set<Long> authorIds  = comments.stream().map(Comment::getUserId).collect(Collectors.toSet());

        Map<Long, User> authors = userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        Map<Long, Long> replyCounts = commentRepository.countRepliesByParentIds(commentIds).stream()
                .collect(Collectors.toMap(CommentRepository.CountByKey::getId,
                                          CommentRepository.CountByKey::getTotal));

        return new PageContext(
                authors,
                commentLikeRepository.findLikedCommentIds(currentUser.getId(), commentIds),
                replyCounts);
    }

    CommentResponse toCommentResponse(Comment comment, User currentUser) {
        return toCommentResponse(comment, currentUser, loadPageContext(List.of(comment), currentUser));
    }

    private CommentResponse toCommentResponse(Comment comment, User currentUser, PageContext ctx) {
        boolean liked = ctx.likedCommentIds().contains(comment.getId());
        long replyCount = ctx.replyCounts().getOrDefault(comment.getId(), 0L);
        long likesCount = comment.getLikesCount() + redisGetDelta(REDIS_COMMENT_LIKES + comment.getId());

        PostAuthorResponse author = resolveAuthor(comment.getUserId(), ctx.authors());
        Comment enriched = new Comment();
        enriched.setId(comment.getId());
        enriched.setPostId(comment.getPostId());
        enriched.setParentCommentId(comment.getParentCommentId());
        enriched.setContent(comment.getContent());
        enriched.setLikesCount((int) likesCount);
        enriched.setCreatedAt(comment.getCreatedAt());
        enriched.setUpdatedAt(comment.getUpdatedAt());

        return commentMapper.toResponse(enriched, author, liked, replyCount);
    }

    private PostAuthorResponse resolveAuthor(Long userId, Map<Long, User> authors) {
        User author = authors.get(userId);
        if (author == null) return new PostAuthorResponse(userId, "Unknown", null, "UNKNOWN");
        // avatar_url holds a bare key since changeset 012 — it has to be signed to be fetchable.
        return new PostAuthorResponse(author.getId(), resolveDisplayName(author),
                mediaUrlResolver.toViewableUrl(author.getAvatarUrl()), author.getRole().name());
    }

    private String resolveDisplayName(User user) {
        if (user instanceof com.unisphere.backend.identity.entity.Student s) return s.getFullName();
        if (user instanceof com.unisphere.backend.identity.entity.Alumni a)  return a.getFullName();
        if (user instanceof com.unisphere.backend.identity.entity.Admin a)   return a.getFullName();
        if (user instanceof com.unisphere.backend.identity.entity.Employer e) return e.getCompanyName();
        if (user instanceof com.unisphere.backend.identity.entity.University u) return u.getName();
        if (user instanceof com.unisphere.backend.identity.entity.Club c)    return c.getName();
        return user.getEmail();
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
