package com.unisphere.backend.social.community.repository;

import com.unisphere.backend.social.community.entity.CommunityBan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommunityBanRepository extends JpaRepository<CommunityBan, Long> {

    Page<CommunityBan> findByCommunityId(Long communityId, Pageable pageable);

    Optional<CommunityBan> findByCommunityIdAndUserId(Long communityId, Long userId);

    boolean existsByCommunityIdAndUserId(Long communityId, Long userId);

    void deleteByCommunityIdAndUserId(Long communityId, Long userId);
}
