package com.unisphere.backend.social.follow.service;

import com.unisphere.backend.common.exception.UserNotFoundException;
import com.unisphere.backend.identity.dto.UserSummaryResponse;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.repository.UserRepository;
import com.unisphere.backend.identity.service.UserService;
import com.unisphere.backend.social.follow.dto.response.FollowStatsResponse;
import com.unisphere.backend.social.follow.dto.response.FollowToggleResponse;
import com.unisphere.backend.social.follow.entity.Follow;
import com.unisphere.backend.social.follow.repository.FollowRepository;
import com.unisphere.backend.social.notification.enums.NotificationType;
import com.unisphere.backend.social.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class FollowService {

    /** Upper bound on a recommendations request — it is a curated strip, not a browsable feed. */
    private static final int MAX_RECOMMENDATIONS = 50;

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final UserService userService;

    public FollowToggleResponse toggleFollow(Long followingId, User currentUser) {
        Long followerId = currentUser.getId();
        if (followingId.equals(followerId)) {
            throw new IllegalArgumentException("Cannot follow yourself");
        }
        if (!userRepository.existsById(followingId)) {
            throw new UserNotFoundException("User not found: " + followingId);
        }

        if (followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            followRepository.deleteByFollowerIdAndFollowingId(followerId, followingId);
            return new FollowToggleResponse(false, followRepository.countByFollowingId(followingId));
        } else {
            followRepository.save(new Follow(followerId, followingId));
            notificationService.createAndPush(followingId, followerId, NotificationType.FOLLOW, followerId, "USER");
            return new FollowToggleResponse(true, followRepository.countByFollowingId(followingId));
        }
    }

    @Transactional(readOnly = true)
    public FollowStatsResponse getStats(Long userId, User currentUser) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException("User not found: " + userId);
        }
        return new FollowStatsResponse(
                followRepository.countByFollowingId(userId),
                followRepository.countByFollowerId(userId),
                followRepository.existsByFollowerIdAndFollowingId(currentUser.getId(), userId)
        );
    }

    /**
     * People the requester may know — friends-of-friends first, then same-university users.
     * Returns empty when the requester follows nobody and has no university (e.g. a brand-new
     * employer account); the UI shows a "search for people" empty state in that case rather than
     * padding the list with arbitrary accounts.
     */
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> getRecommendations(User currentUser, int limit) {
        int capped = Math.clamp(limit, 1, MAX_RECOMMENDATIONS);
        List<Long> ids = followRepository.findRecommendedUserIds(
                currentUser.getId(), UserService.universityIdOf(currentUser), capped);
        return userService.summariesFor(ids, currentUser);
    }

    @Transactional(readOnly = true)
    public Page<UserSummaryResponse> getFollowers(Long userId, User currentUser, Pageable pageable) {
        return mapPage(followRepository.findFollowerIds(userId, pageable), currentUser);
    }

    @Transactional(readOnly = true)
    public Page<UserSummaryResponse> getFollowing(Long userId, User currentUser, Pageable pageable) {
        return mapPage(followRepository.findFollowingIds(userId, pageable), currentUser);
    }

    private Page<UserSummaryResponse> mapPage(Page<Long> ids, User currentUser) {
        List<UserSummaryResponse> summaries = userService.summariesFor(ids.getContent(), currentUser);
        return new org.springframework.data.domain.PageImpl<>(
                summaries, ids.getPageable(), ids.getTotalElements());
    }
}
