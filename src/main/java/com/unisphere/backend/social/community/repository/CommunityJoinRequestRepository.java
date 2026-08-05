package com.unisphere.backend.social.community.repository;

import com.unisphere.backend.social.community.entity.CommunityJoinRequest;
import com.unisphere.backend.social.community.enums.JoinRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CommunityJoinRequestRepository extends JpaRepository<CommunityJoinRequest, Long> {

    Page<CommunityJoinRequest> findByCommunityIdAndStatus(Long communityId, JoinRequestStatus status, Pageable pageable);

    boolean existsByCommunityIdAndUserIdAndStatus(Long communityId, Long userId, JoinRequestStatus status);

    /** Batched "does the viewer have a pending request" lookup for a list page. */
    List<CommunityJoinRequest> findByCommunityIdInAndUserIdAndStatus(
            Collection<Long> communityIds, Long userId, JoinRequestStatus status);
}
