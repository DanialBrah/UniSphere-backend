package com.unisphere.backend.social.posting.repository;

import com.unisphere.backend.social.posting.entity.PostSave;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostSaveRepository extends JpaRepository<PostSave, Long> {

    boolean existsByUserIdAndPostId(Long userId, Long postId);

    void deleteByUserIdAndPostId(Long userId, Long postId);

    Page<PostSave> findByUserIdOrderBySavedAtDesc(Long userId, Pageable pageable);
}
