package com.unisphere.backend.social.community.service;

import com.unisphere.backend.common.exception.NotCommunityMemberException;
import com.unisphere.backend.common.exception.UnauthorizedActionException;
import com.unisphere.backend.identity.entity.Alumni;
import com.unisphere.backend.identity.entity.Club;
import com.unisphere.backend.identity.entity.Role;
import com.unisphere.backend.identity.entity.Student;
import com.unisphere.backend.identity.entity.University;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.social.community.entity.Community;
import com.unisphere.backend.social.community.entity.CommunityMember;
import com.unisphere.backend.social.community.enums.CommunityMemberRole;
import com.unisphere.backend.social.community.repository.CommunityMemberRepository;
import com.unisphere.backend.social.community.repository.CommunityPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Who may see a community's content, and who may act on it. A community's existence and basic
 * info (name, description, member count) is always discoverable — see
 * {@link com.unisphere.backend.social.community.repository.CommunityRepository#search}. This
 * service instead gates *content*: members, posts, announcements, and chat.
 */
@Service
@RequiredArgsConstructor
public class CommunityAccessService {

    private final CommunityMemberRepository communityMemberRepository;
    private final CommunityPostRepository communityPostRepository;

    public boolean canViewContent(Community community, User viewer) {
        if (community.getCreatedBy().equals(viewer.getId())) return true;
        if (viewer.getRole() == Role.ADMIN) return true;
        if (isMember(community.getId(), viewer.getId())) return true;
        return switch (community.getVisibility()) {
            case PUBLIC -> true;
            case UNIVERSITY_ONLY -> community.getUniversityId() != null
                    && community.getUniversityId().equals(viewerUniversityId(viewer));
            case PRIVATE -> false;
        };
    }

    /** Whether {@code viewer} may see a specific community post — consumed by PostAccessService's COMMUNITY case. */
    public boolean canViewPost(Long postId, User viewer) {
        if (viewer.getRole() == Role.ADMIN) return true;
        return communityPostRepository.findByPostId(postId)
                .map(cp -> isMember(cp.getCommunityId(), viewer.getId()))
                .orElse(false);
    }

    public boolean isMember(Long communityId, Long userId) {
        return communityMemberRepository.existsByCommunityIdAndUserId(communityId, userId);
    }

    public Optional<CommunityMemberRole> roleOf(Long communityId, Long userId) {
        return communityMemberRepository.findByCommunityIdAndUserId(communityId, userId)
                .map(CommunityMember::getRole);
    }

    public void assertMember(Long communityId, Long userId) {
        if (!isMember(communityId, userId)) {
            throw new NotCommunityMemberException();
        }
    }

    public void assertModerator(Long communityId, Long userId) {
        CommunityMemberRole role = roleOf(communityId, userId).orElseThrow(NotCommunityMemberException::new);
        if (role != CommunityMemberRole.ADMIN && role != CommunityMemberRole.MODERATOR) {
            throw new UnauthorizedActionException("Only community admins or moderators can perform this action");
        }
    }

    public void assertAdmin(Long communityId, Long userId) {
        CommunityMemberRole role = roleOf(communityId, userId).orElseThrow(NotCommunityMemberException::new);
        if (role != CommunityMemberRole.ADMIN) {
            throw new UnauthorizedActionException("Only community admins can perform this action");
        }
    }

    public Long viewerUniversityId(User user) {
        if (user instanceof Student s)    return s.getUniversityId();
        if (user instanceof Alumni a)     return a.getUniversityId();
        if (user instanceof Club c)       return c.getUniversityId();
        if (user instanceof University u) return u.getId();
        return null; // Employer, Admin — no university affiliation
    }
}
