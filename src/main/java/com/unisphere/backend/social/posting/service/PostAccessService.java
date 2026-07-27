package com.unisphere.backend.social.posting.service;

import com.unisphere.backend.identity.entity.Role;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.social.follow.repository.FollowRepository;
import com.unisphere.backend.social.posting.entity.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostAccessService {

    private final FollowRepository followRepository;

    public boolean canView(Post post, User viewer) {
        if (post.getUserId().equals(viewer.getId())) return true;
        if (viewer.getRole() == Role.ADMIN) return true;
        return switch (post.getVisibility()) {
            case PUBLIC -> true;
            case UNIVERSITY -> {
                Long viewerUni = viewerUniversityId(viewer);
                yield post.getUniversityId() != null && post.getUniversityId().equals(viewerUni);
            }
            case FRIENDS -> isMutualFollow(viewer.getId(), post.getUserId());
            case PRIVATE -> false;
        };
    }

    public Long viewerUniversityId(User user) {
        if (user instanceof com.unisphere.backend.identity.entity.Student s)  return s.getUniversityId();
        if (user instanceof com.unisphere.backend.identity.entity.Alumni a)   return a.getUniversityId();
        if (user instanceof com.unisphere.backend.identity.entity.Club c)    return c.getUniversityId();
        if (user instanceof com.unisphere.backend.identity.entity.University u) return u.getId();
        return null; // Employer, Admin — no university affiliation
    }

    public boolean isMutualFollow(Long userA, Long userB) {
        return followRepository.existsByFollowerIdAndFollowingId(userA, userB)
                && followRepository.existsByFollowerIdAndFollowingId(userB, userA);
    }
}
