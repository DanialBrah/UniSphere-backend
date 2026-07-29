package com.unisphere.backend.social.follow.dto.response;

/**
 * Follow-graph counters for one user, plus whether the requester follows them.
 * <p>
 * Deliberately a separate endpoint rather than extra fields on {@code UserProfileResponse}: that
 * is a sealed interface with six record implementations, each fully mapped by MapStruct, and the
 * same mappers are called from {@code AuthService} during login and registration — so adding
 * fields there would ripple through the auth path for data it has no use for.
 */
public record FollowStatsResponse(
        long followersCount,
        long followingCount,
        boolean isFollowing
) {}
