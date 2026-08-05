package com.unisphere.backend.social.community.repository;

import com.unisphere.backend.social.community.entity.CommunityMember;
import com.unisphere.backend.social.community.enums.CommunityMemberRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommunityMemberRepository extends JpaRepository<CommunityMember, Long> {

    List<CommunityMember> findByCommunityId(Long communityId);

    Page<CommunityMember> findByCommunityId(Long communityId, Pageable pageable);

    /** Batched form of {@link #findByCommunityId} for rendering a page of communities. */
    List<CommunityMember> findByCommunityIdIn(Collection<Long> communityIds);

    /** Batched "what's the viewer's role in each of these communities" lookup for a list page. */
    List<CommunityMember> findByCommunityIdInAndUserId(Collection<Long> communityIds, Long userId);

    Optional<CommunityMember> findByCommunityIdAndUserId(Long communityId, Long userId);

    boolean existsByCommunityIdAndUserId(Long communityId, Long userId);

    long countByCommunityIdAndRole(Long communityId, CommunityMemberRole role);

    void deleteByCommunityIdAndUserId(Long communityId, Long userId);
}
