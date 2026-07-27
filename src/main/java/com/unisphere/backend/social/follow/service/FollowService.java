package com.unisphere.backend.social.follow.service;

import com.unisphere.backend.common.exception.UserNotFoundException;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.repository.UserRepository;
import com.unisphere.backend.social.follow.dto.response.FollowToggleResponse;
import com.unisphere.backend.social.follow.entity.Follow;
import com.unisphere.backend.social.follow.repository.FollowRepository;
import com.unisphere.backend.social.notification.enums.NotificationType;
import com.unisphere.backend.social.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

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
}
