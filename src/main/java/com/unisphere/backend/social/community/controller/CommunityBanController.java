package com.unisphere.backend.social.community.controller;

import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.social.community.dto.request.BanRequest;
import com.unisphere.backend.social.community.dto.response.CommunityBanResponse;
import com.unisphere.backend.social.community.service.CommunityMembershipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/communities/{communityId}/bans")
@RequiredArgsConstructor
public class CommunityBanController {

    private final CommunityMembershipService communityMembershipService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<CommunityBanResponse>>> listBans(
            @PathVariable Long communityId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                communityMembershipService.listBans(communityId, pageable, currentUser)));
    }

    @PostMapping("/{userId}")
    public ResponseEntity<ApiResponse<CommunityBanResponse>> ban(
            @PathVariable Long communityId,
            @PathVariable Long userId,
            @Valid @RequestBody BanRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                communityMembershipService.ban(communityId, userId, req, currentUser), "Member banned"));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> unban(
            @PathVariable Long communityId,
            @PathVariable Long userId,
            @AuthenticationPrincipal User currentUser) {
        communityMembershipService.unban(communityId, userId, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Member unbanned"));
    }
}
