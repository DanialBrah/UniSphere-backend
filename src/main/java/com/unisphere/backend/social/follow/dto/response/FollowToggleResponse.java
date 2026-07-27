package com.unisphere.backend.social.follow.dto.response;

public record FollowToggleResponse(
        boolean following,
        long followersCount
) {}
