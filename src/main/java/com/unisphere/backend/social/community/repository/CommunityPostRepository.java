package com.unisphere.backend.social.community.repository;

import com.unisphere.backend.social.community.entity.CommunityPost;
import com.unisphere.backend.social.posting.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {

    /** A community's post feed, newest first. */
    @Query("""
            SELECT p FROM Post p, CommunityPost cp
            WHERE cp.postId = p.id AND cp.communityId = :communityId
            ORDER BY p.createdAt DESC
            """)
    Page<Post> findFeed(@Param("communityId") Long communityId, Pageable pageable);

    boolean existsByCommunityIdAndPostId(Long communityId, Long postId);

    /** Reverse lookup — which community (if any) owns this post. Used by CommunityAccessService. */
    Optional<CommunityPost> findByPostId(Long postId);
}
