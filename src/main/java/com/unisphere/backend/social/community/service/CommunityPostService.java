package com.unisphere.backend.social.community.service;

import com.unisphere.backend.common.exception.CommunityNotFoundException;
import com.unisphere.backend.common.exception.PostNotFoundException;
import com.unisphere.backend.common.exception.UnauthorizedActionException;
import com.unisphere.backend.identity.entity.Role;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.social.community.entity.Community;
import com.unisphere.backend.social.community.entity.CommunityPost;
import com.unisphere.backend.social.community.enums.CommunityMemberRole;
import com.unisphere.backend.social.community.repository.CommunityPostRepository;
import com.unisphere.backend.social.community.repository.CommunityRepository;
import com.unisphere.backend.social.posting.dto.request.CreatePostRequest;
import com.unisphere.backend.social.posting.dto.response.PostResponse;
import com.unisphere.backend.social.posting.entity.Post;
import com.unisphere.backend.social.posting.enums.PostVisibility;
import com.unisphere.backend.social.posting.repository.PostRepository;
import com.unisphere.backend.social.posting.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
@RequiredArgsConstructor
public class CommunityPostService {

    private final CommunityRepository communityRepository;
    private final CommunityPostRepository communityPostRepository;
    private final PostRepository postRepository;
    private final PostService postService;
    private final CommunityAccessService communityAccessService;

    /**
     * Requires membership even in a PUBLIC community — anyone can read a public community's feed,
     * but posting is member-only. The client-supplied visibility/universityId (if any) are
     * ignored: every community post is forced to PostVisibility.COMMUNITY.
     */
    public PostResponse createPost(Long communityId, CreatePostRequest req, User currentUser) {
        communityRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));
        communityAccessService.assertMember(communityId, currentUser.getId());

        CreatePostRequest forced = new CreatePostRequest(
                req.title(), req.content(), req.postType(), PostVisibility.COMMUNITY, null,
                req.taggedUserIds(), req.media());
        PostResponse response = postService.createPost(forced, currentUser);

        CommunityPost link = new CommunityPost();
        link.setCommunityId(communityId);
        link.setPostId(response.id());
        communityPostRepository.save(link);

        return response;
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getFeed(Long communityId, Pageable pageable, User currentUser) {
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));
        if (!communityAccessService.canViewContent(community, currentUser)) {
            throw new CommunityNotFoundException(communityId);
        }
        Page<Post> posts = communityPostRepository.findFeed(communityId, pageable);
        return postService.toResponses(posts, currentUser);
    }

    /** Author, community ADMIN/MODERATOR, or global ADMIN may remove a community post. */
    public void removePost(Long communityId, Long postId, User currentUser) {
        if (!communityPostRepository.existsByCommunityIdAndPostId(communityId, postId)) {
            throw new PostNotFoundException(postId);
        }
        Post post = postRepository.findActiveById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));

        boolean isAuthor = post.getUserId().equals(currentUser.getId());
        boolean isGlobalAdmin = currentUser.getRole() == Role.ADMIN;
        boolean isCommunityMod = communityAccessService.roleOf(communityId, currentUser.getId())
                .map(r -> r == CommunityMemberRole.ADMIN || r == CommunityMemberRole.MODERATOR)
                .orElse(false);
        if (!isAuthor && !isGlobalAdmin && !isCommunityMod) {
            throw new UnauthorizedActionException("You cannot remove this post");
        }

        post.setDeletedAt(LocalDateTime.now());
        postRepository.save(post);
    }
}
