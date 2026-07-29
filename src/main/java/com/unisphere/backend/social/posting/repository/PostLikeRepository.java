package com.unisphere.backend.social.posting.repository;

import com.unisphere.backend.social.posting.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    /** Batched form of {@link #existsByPostIdAndUserId} for rendering a whole page of posts. */
    @Query("SELECT pl.postId FROM PostLike pl WHERE pl.userId = :userId AND pl.postId IN :postIds")
    Set<Long> findLikedPostIds(@Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);

    void deleteByPostIdAndUserId(Long postId, Long userId);

    long countByPostId(Long postId);
}
