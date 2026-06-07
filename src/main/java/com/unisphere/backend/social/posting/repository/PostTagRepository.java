package com.unisphere.backend.social.posting.repository;

import com.unisphere.backend.social.posting.entity.PostTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostTagRepository extends JpaRepository<PostTag, Long> {

    List<PostTag> findByPostId(Long postId);
}
