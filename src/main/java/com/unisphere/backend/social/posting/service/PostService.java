package com.unisphere.backend.social.posting.service;

import com.unisphere.backend.common.exception.PostNotFoundException;
import com.unisphere.backend.common.exception.UnauthorizedActionException;
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
import com.unisphere.backend.social.posting.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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

    public PostResponse createPost(CreatePostRequest req, User currentUser) {
        Post post = new Post();
        post.setUserId(currentUser.getId());
        post.setTitle(req.title());
        post.setContent(req.content());
        post.setPostType(req.postType() != null ? req.postType() : PostType.TEXT);
        post.setVisibility(req.visibility() != null ? req.visibility() : PostVisibility.PUBLIC);
        post.setUniversityId(req.universityId());

        if (req.media() != null) {
            int order = 0;
            for (CreatePostRequest.MediaItem item : req.media()) {
                PostMedia media = new PostMedia();
                media.setPost(post);
                media.setMediaUrl(item.mediaKey());
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
        return toPostResponse(post, currentUser);
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getFeed(Pageable pageable, User currentUser) {
        return postRepository
                .findByVisibilityOrderByCreatedAtDesc(PostVisibility.PUBLIC, pageable)
                .map(post -> toPostResponse(post, currentUser));
    }

    @Transactional(readOnly = true)
    public PostResponse getPostById(Long postId, User currentUser) {
        Post post = findActivePost(postId);
        redisIncrement(REDIS_POST_VIEWS + postId, 1);
        return toPostResponse(post, currentUser);
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getPostsByUser(Long userId, Pageable pageable, User currentUser) {
        return postRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(post -> toPostResponse(post, currentUser));
    }

    public PostResponse updatePost(Long postId, UpdatePostRequest req, User currentUser) {
        Post post = findActivePost(postId);
        assertOwner(post.getUserId(), currentUser);

        if (req.title() != null)      post.setTitle(req.title());
        if (req.content() != null)    post.setContent(req.content());
        if (req.visibility() != null) post.setVisibility(req.visibility());

        return toPostResponse(postRepository.save(post), currentUser);
    }

    public void deletePost(Long postId, User currentUser) {
        Post post = findActivePost(postId);
        if (!post.getUserId().equals(currentUser.getId()) && currentUser.getRole() != Role.ADMIN) {
            throw new UnauthorizedActionException("You cannot delete this post");
        }
        post.setDeletedAt(LocalDateTime.now());
        postRepository.save(post);
    }

    public LikeToggleResponse toggleLike(Long postId, User currentUser) {
        findActivePost(postId);
        Long userId = currentUser.getId();

        if (postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            postLikeRepository.deleteByPostIdAndUserId(postId, userId);
            redisIncrement(REDIS_POST_LIKES + postId, -1);
            return new LikeToggleResponse(false, postLikeRepository.countByPostId(postId));
        } else {
            postLikeRepository.save(new PostLike(postId, userId));
            redisIncrement(REDIS_POST_LIKES + postId, 1);
            return new LikeToggleResponse(true, postLikeRepository.countByPostId(postId));
        }
    }

    public SaveToggleResponse toggleSave(Long postId, User currentUser) {
        findActivePost(postId);
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
    public Page<PostResponse> searchPosts(String query, Pageable pageable, User currentUser) {
        return postRepository.searchFullText(query, pageable)
                .map(post -> toPostResponse(post, currentUser));
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

    PostResponse toPostResponse(Post post, User currentUser) {
        Long userId = currentUser.getId();
        boolean liked = postLikeRepository.existsByPostIdAndUserId(post.getId(), userId);
        boolean saved = postSaveRepository.existsByUserIdAndPostId(userId, post.getId());
        long commentCount = commentRepository.countByPostIdAndParentCommentIdIsNull(post.getId());

        List<PostMediaResponse> media = post.getMedia().stream()
                .map(postMapper::toMediaResponse)
                .toList();

        List<Long> taggedIds = post.getTags().stream()
                .map(PostTag::getTaggedUserId)
                .toList();

        PostAuthorResponse author = resolveAuthor(post.getUserId());

        long likesCount = post.getLikesCount() + redisGetDelta(REDIS_POST_LIKES + post.getId());
        long viewsCount = post.getViewsCount() + redisGetDelta(REDIS_POST_VIEWS + post.getId());

        return new PostResponse(
                post.getId(), author, post.getTitle(), post.getContent(),
                post.getPostType(), post.getVisibility(), post.getUniversityId(),
                post.isPinned(), likesCount, viewsCount, commentCount,
                liked, saved, media, taggedIds, post.getCreatedAt(), post.getUpdatedAt()
        );
    }

    private PostAuthorResponse resolveAuthor(Long userId) {
        return userRepository.findById(userId)
                .map(u -> new PostAuthorResponse(u.getId(), resolveDisplayName(u), u.getAvatarUrl(), u.getRole().name()))
                .orElse(new PostAuthorResponse(userId, "Unknown", null, "UNKNOWN"));
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
