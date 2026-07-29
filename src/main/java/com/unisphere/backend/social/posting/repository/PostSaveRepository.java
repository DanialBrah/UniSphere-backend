package com.unisphere.backend.social.posting.repository;

import com.unisphere.backend.social.posting.entity.PostSave;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;

public interface PostSaveRepository extends JpaRepository<PostSave, Long> {

    boolean existsByUserIdAndPostId(Long userId, Long postId);

    /** Batched form of {@link #existsByUserIdAndPostId} for rendering a whole page of posts. */
    @Query("SELECT ps.postId FROM PostSave ps WHERE ps.userId = :userId AND ps.postId IN :postIds")
    Set<Long> findSavedPostIds(@Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);

    void deleteByUserIdAndPostId(Long userId, Long postId);

    Page<PostSave> findByUserIdOrderBySavedAtDesc(Long userId, Pageable pageable);
}
