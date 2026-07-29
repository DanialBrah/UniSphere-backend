package com.unisphere.backend.social.posting.service;

import com.unisphere.backend.common.exception.PostNotFoundException;
import com.unisphere.backend.common.exception.UnauthorizedActionException;
import com.unisphere.backend.common.storage.MediaUrlResolver;
import com.unisphere.backend.identity.entity.Role;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.repository.UserRepository;
import com.unisphere.backend.social.posting.dto.request.CreatePostRequest;
import com.unisphere.backend.social.posting.dto.request.UpdatePostRequest;
import com.unisphere.backend.social.posting.dto.response.*;
import com.unisphere.backend.social.posting.entity.*;
import com.unisphere.backend.social.posting.enums.MediaType;
import com.unisphere.backend.social.posting.enums.PostType;
import com.unisphere.backend.social.posting.enums.PostVisibility;
import com.unisphere.backend.social.posting.mapper.PostMapper;
import com.unisphere.backend.social.notification.enums.NotificationType;
import com.unisphere.backend.social.notification.service.NotificationService;
import com.unisphere.backend.social.posting.repository.*;
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
public class PostService {

    private static final String REDIS_POST_LIKES  = "post:likes:";
    private static final String REDIS_POST_VIEWS  = "post:views:";

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostSaveRepository postSaveRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final PostMapper postMapper;
    private final RedisTemplate<String, Long> redisTemplate;
    private final NotificationService notificationService;
    private final MediaUrlResolver mediaUrlResolver;
    private final PostAccessService postAccessService;

    public PostResponse createPost(CreatePostRequest req, User currentUser) {
        Post post = new Post();
        post.setUserId(currentUser.getId());
        post.setTitle(req.title());
        post.setContent(req.content());
        post.setPostType(req.postType() != null ? req.postType() : PostType.TEXT);
        PostVisibility visibility = req.visibility() != null ? req.visibility() : PostVisibility.PUBLIC;
        post.setVisibility(visibility);
        // Never trust a client-supplied universityId — UNIVERSITY visibility's guarantee depends on
        // this actually matching the poster's own affiliation, not an arbitrary client value.
        post.setUniversityId(visibility == PostVisibility.UNIVERSITY
                ? postAccessService.viewerUniversityId(currentUser)
                : req.universityId());

        if (req.media() != null) {
            int order = 0;
            for (CreatePostRequest.MediaItem item : req.media()) {
                PostMedia media = new PostMedia();
                media.setPost(post);
                media.setMediaKey(item.mediaKey());
                media.setMediaType(resolveMediaType(item.mediaType()));
                media.setSortOrder(order++);
                post.getMedia().add(media);
            }
        }

        if (req.taggedUserIds() != null) {
            for (Long taggedId : req.taggedUserIds()) {
                PostTag tag = new PostTag();
                tag.setPost(post);
                tag.setTaggedUserId(taggedId);
                post.getTags().add(tag);
            }
        }

        postRepository.save(post);

        if (req.taggedUserIds() != null) {
            for (Long taggedId : req.taggedUserIds()) {
                notificationService.createAndPush(taggedId, currentUser.getId(), NotificationType.MENTION, post.getId(), "POST");
            }
        }

        return toPostResponse(post, currentUser);
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getFeed(Pageable pageable, User currentUser) {
        return toPostResponses(
                postRepository.findByVisibilityOrderByCreatedAtDesc(PostVisibility.PUBLIC, pageable),
                currentUser);
    }

    @Transactional(readOnly = true)
    public PostResponse getPostById(Long postId, User currentUser) {
        Post post = findActivePost(postId);
        if (!postAccessService.canView(post, currentUser)) {
            throw new PostNotFoundException(postId);
        }
        redisIncrement(REDIS_POST_VIEWS + postId, 1);
        return toPostResponse(post, currentUser);
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getPostsByUser(Long userId, Pageable pageable, User currentUser) {
        boolean isOwnerOrAdmin = userId.equals(currentUser.getId()) || currentUser.getRole() == Role.ADMIN;
        boolean isFriend = isOwnerOrAdmin || postAccessService.isMutualFollow(currentUser.getId(), userId);
        return toPostResponses(
                postRepository.findByUserIdVisibleTo(userId, isOwnerOrAdmin,
                        postAccessService.viewerUniversityId(currentUser), isFriend, pageable),
                currentUser);
    }

    public PostResponse updatePost(Long postId, UpdatePostRequest req, User currentUser) {
        Post post = findActivePost(postId);
        if (!postAccessService.canView(post, currentUser)) {
            throw new PostNotFoundException(postId);
        }
        assertOwner(post.getUserId(), currentUser);

        if (req.title() != null)      post.setTitle(req.title());
        if (req.content() != null)    post.setContent(req.content());
        if (req.visibility() != null) post.setVisibility(req.visibility());

        if (req.removeMediaIds() != null && !req.removeMediaIds().isEmpty()) {
            post.getMedia().removeIf(m -> req.removeMediaIds().contains(m.getId()));
        }

        if (req.addMedia() != null && !req.addMedia().isEmpty()) {
            int nextOrder = post.getMedia().stream()
                    .mapToInt(PostMedia::getSortOrder).max().orElse(-1) + 1;
            for (UpdatePostRequest.MediaItem item : req.addMedia()) {
                PostMedia m = new PostMedia();
                m.setPost(post);
                m.setMediaKey(item.mediaKey());
                m.setMediaType(resolveMediaType(item.mediaType()));
                m.setSortOrder(nextOrder++);
                post.getMedia().add(m);
            }
        }

        return toPostResponse(postRepository.save(post), currentUser);
    }

    public void deletePost(Long postId, User currentUser) {
        Post post = findActivePost(postId);
        if (!postAccessService.canView(post, currentUser)) {
            throw new PostNotFoundException(postId);
        }
        if (!post.getUserId().equals(currentUser.getId()) && currentUser.getRole() != Role.ADMIN) {
            throw new UnauthorizedActionException("You cannot delete this post");
        }
        post.setDeletedAt(LocalDateTime.now());
        postRepository.save(post);
    }

    public LikeToggleResponse toggleLike(Long postId, User currentUser) {
        Post post = findActivePost(postId);
        if (!postAccessService.canView(post, currentUser)) {
            throw new PostNotFoundException(postId);
        }
        Long userId = currentUser.getId();

        if (postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            postLikeRepository.deleteByPostIdAndUserId(postId, userId);
            redisIncrement(REDIS_POST_LIKES + postId, -1);
            return new LikeToggleResponse(false, postLikeRepository.countByPostId(postId));
        } else {
            postLikeRepository.save(new PostLike(postId, userId));
            redisIncrement(REDIS_POST_LIKES + postId, 1);
            notificationService.createAndPush(post.getUserId(), userId, NotificationType.LIKE, postId, "POST");
            return new LikeToggleResponse(true, postLikeRepository.countByPostId(postId));
        }
    }

    public SaveToggleResponse toggleSave(Long postId, User currentUser) {
        Post post = findActivePost(postId);
        if (!postAccessService.canView(post, currentUser)) {
            throw new PostNotFoundException(postId);
        }
        Long userId = currentUser.getId();

        if (postSaveRepository.existsByUserIdAndPostId(userId, postId)) {
            postSaveRepository.deleteByUserIdAndPostId(userId, postId);
            return new SaveToggleResponse(false);
        } else {
            postSaveRepository.save(new PostSave(userId, postId));
            return new SaveToggleResponse(true);
        }
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getLikedPosts(Pageable pageable, User currentUser) {
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        return toPostResponses(
                postRepository.findLikedPostsByUserId(currentUser.getId(), isAdmin,
                        postAccessService.viewerUniversityId(currentUser), pageable),
                currentUser);
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getSavedPosts(Pageable pageable, User currentUser) {
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        return toPostResponses(
                postRepository.findSavedPostsByUserId(currentUser.getId(), isAdmin,
                        postAccessService.viewerUniversityId(currentUser), pageable),
                currentUser);
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> searchPosts(String query, Pageable pageable, User currentUser) {
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        return toPostResponses(
                postRepository.searchFullText(query, currentUser.getId(), isAdmin,
                        postAccessService.viewerUniversityId(currentUser), pageable),
                currentUser);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Post findActivePost(Long postId) {
        return postRepository.findActiveById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
    }

    private void assertOwner(Long ownerId, User currentUser) {
        if (!ownerId.equals(currentUser.getId())) {
            throw new UnauthorizedActionException("You do not own this resource");
        }
    }

    /**
     * Page-level mapping: authors are batch-loaded once for the whole page. Mapping post-by-post
     * would issue one author lookup per post, so a 20-post feed costs 20 extra round-trips.
     */
    private Page<PostResponse> toPostResponses(Page<Post> posts, User currentUser) {
        PageContext ctx = loadPageContext(posts.getContent(), currentUser);
        return posts.map(post -> toPostResponse(post, currentUser, ctx));
    }

    /**
     * Everything a page of posts needs that would otherwise be fetched per post: authors, the
     * viewer's like/save flags, and comment counts. Four queries for the page instead of four
     * per post.
     */
    private record PageContext(Map<Long, User> authors, Set<Long> likedPostIds,
                               Set<Long> savedPostIds, Map<Long, Long> commentCounts) {}

    private PageContext loadPageContext(List<Post> posts, User currentUser) {
        if (posts.isEmpty()) return new PageContext(Map.of(), Set.of(), Set.of(), Map.of());

        Set<Long> postIds = posts.stream().map(Post::getId).collect(Collectors.toSet());
        Long viewerId = currentUser.getId();

        Map<Long, Long> commentCounts = commentRepository.countTopLevelByPostIds(postIds).stream()
                .collect(Collectors.toMap(CommentRepository.CountByKey::getId,
                                          CommentRepository.CountByKey::getTotal));

        return new PageContext(
                loadAuthors(posts),
                postLikeRepository.findLikedPostIds(viewerId, postIds),
                postSaveRepository.findSavedPostIds(viewerId, postIds),
                commentCounts);
    }

    private Map<Long, User> loadAuthors(List<Post> posts) {
        Set<Long> authorIds = posts.stream()
                .map(Post::getUserId)
                .collect(Collectors.toSet());
        if (authorIds.isEmpty()) return Map.of();
        return userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    PostResponse toPostResponse(Post post, User currentUser) {
        return toPostResponse(post, currentUser, loadPageContext(List.of(post), currentUser));
    }

    private PostResponse toPostResponse(Post post, User currentUser, PageContext ctx) {
        boolean liked = ctx.likedPostIds().contains(post.getId());
        boolean saved = ctx.savedPostIds().contains(post.getId());
        long commentCount = ctx.commentCounts().getOrDefault(post.getId(), 0L);

        // Presigning happens in PostMapper — every post, not just restricted ones, because a plain
        // public URL only resolves on GCS (Garage has no anonymous access, B2 buckets are private).
        List<PostMediaResponse> media = post.getMedia().stream()
                .map(postMapper::toMediaResponse)
                .toList();

        List<Long> taggedIds = post.getTags().stream()
                .map(PostTag::getTaggedUserId)
                .toList();

        PostAuthorResponse author = resolveAuthor(post.getUserId(), ctx.authors());

        long likesCount = post.getLikesCount() + redisGetDelta(REDIS_POST_LIKES + post.getId());
        long viewsCount = post.getViewsCount() + redisGetDelta(REDIS_POST_VIEWS + post.getId());

        return new PostResponse(
                post.getId(), author, post.getTitle(), post.getContent(),
                post.getPostType(), post.getVisibility(), post.getUniversityId(),
                post.isPinned(), likesCount, viewsCount, commentCount,
                liked, saved, media, taggedIds, post.getCreatedAt(), post.getUpdatedAt()
        );
    }

    private PostAuthorResponse resolveAuthor(Long userId, Map<Long, User> authors) {
        User author = authors.get(userId);
        if (author == null) return new PostAuthorResponse(userId, "Unknown", null, "UNKNOWN");
        return new PostAuthorResponse(author.getId(), resolveDisplayName(author),
                mediaUrlResolver.toViewableUrl(author.getAvatarUrl()), author.getRole().name());
    }

    private String resolveDisplayName(User user) {
        // Delegate to the concrete subclass's toString-equivalent
        // Cast to known subtypes to get displayable name
        if (user instanceof com.unisphere.backend.identity.entity.Student s) return s.getFullName();
        if (user instanceof com.unisphere.backend.identity.entity.Alumni a)  return a.getFullName();
        if (user instanceof com.unisphere.backend.identity.entity.Admin a)   return a.getFullName();
        if (user instanceof com.unisphere.backend.identity.entity.Employer e) return e.getCompanyName();
        if (user instanceof com.unisphere.backend.identity.entity.University u) return u.getName();
        if (user instanceof com.unisphere.backend.identity.entity.Club c)    return c.getName();
        return user.getEmail();
    }

    private MediaType resolveMediaType(String rawType) {
        if (rawType == null) return MediaType.IMAGE;
        return rawType.toLowerCase().startsWith("video") ? MediaType.VIDEO : MediaType.IMAGE;
    }

    private long redisGetDelta(String key) {
        try {
            Long v = redisTemplate.opsForValue().get(key);
            return v != null ? v : 0L;
        } catch (Exception ex) {
            return 0L;
        }
    }

    private void redisIncrement(String key, long delta) {
        try {
            if (delta >= 0) redisTemplate.opsForValue().increment(key, delta);
            else            redisTemplate.opsForValue().decrement(key, -delta);
        } catch (Exception ex) {
            // Redis unavailable — counter will sync from DB on next flush
        }
    }
}
