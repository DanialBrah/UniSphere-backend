package com.unisphere.backend.social.posting.repository;

import com.unisphere.backend.social.posting.entity.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {

    boolean existsByCommentIdAndUserId(Long commentId, Long userId);

    @Transactional
    void deleteByCommentIdAndUserId(Long commentId, Long userId);

    long countByCommentId(Long commentId);
}
