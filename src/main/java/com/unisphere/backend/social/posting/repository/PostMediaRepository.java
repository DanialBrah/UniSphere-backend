package com.unisphere.backend.social.posting.repository;

import com.unisphere.backend.social.posting.entity.PostMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostMediaRepository extends JpaRepository<PostMedia, Long> {

    List<PostMedia> findByPost_IdOrderBySortOrderAsc(Long postId);
}
