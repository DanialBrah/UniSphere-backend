package com.unisphere.backend.social.follow.controller;

import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.identity.dto.UserSummaryResponse;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.social.follow.dto.response.FollowStatsResponse;
import com.unisphere.backend.social.follow.dto.response.FollowToggleResponse;
import com.unisphere.backend.social.follow.service.FollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Follow", description = "Follow graph: toggle, stats, followers/following and recommendations")
public class FollowController {

    private final FollowService followService;

    @Operation(summary = "Follow or unfollow a user (toggles)")
    @PostMapping("/{userId}/follow")
    public ResponseEntity<ApiResponse<FollowToggleResponse>> toggleFollow(
            @PathVariable Long userId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(followService.toggleFollow(userId, currentUser)));
    }

    @Operation(summary = "Follower/following counts for a user, plus whether you follow them")
    @GetMapping("/{userId}/follow-stats")
    public ResponseEntity<ApiResponse<FollowStatsResponse>> followStats(
            @PathVariable Long userId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(followService.getStats(userId, currentUser)));
    }

    @Operation(summary = "Users who follow this user")
    @GetMapping("/{userId}/followers")
    public ResponseEntity<ApiResponse<Page<UserSummaryResponse>>> followers(
            @PathVariable Long userId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(followService.getFollowers(userId, currentUser, pageable)));
    }

    @Operation(summary = "Users this user follows")
    @GetMapping("/{userId}/following")
    public ResponseEntity<ApiResponse<Page<UserSummaryResponse>>> following(
            @PathVariable Long userId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(followService.getFollowing(userId, currentUser, pageable)));
    }

    @Operation(
            summary = "People you may know",
            description = "Friends-of-friends first (ranked by number of mutual connections), "
                    + "backfilled with users from your university. Excludes people you already follow."
    )
    @GetMapping("/recommendations")
    public ResponseEntity<ApiResponse<List<UserSummaryResponse>>> recommendations(
            @RequestParam(defaultValue = "12") int limit,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(followService.getRecommendations(currentUser, limit)));
    }
}
