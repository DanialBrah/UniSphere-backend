package com.unisphere.backend.social.community.controller;

import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.social.community.dto.request.ChangeRoleRequest;
import com.unisphere.backend.social.community.dto.request.CreateJoinRequestRequest;
import com.unisphere.backend.social.community.dto.response.CommunityJoinRequestResponse;
import com.unisphere.backend.social.community.dto.response.CommunityMemberResponse;
import com.unisphere.backend.social.community.enums.JoinRequestStatus;
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
@RequestMapping("/api/v1/communities/{communityId}")
@RequiredArgsConstructor
public class CommunityMembershipController {

    private final CommunityMembershipService communityMembershipService;

    @GetMapping("/members")
    public ResponseEntity<ApiResponse<Page<CommunityMemberResponse>>> listMembers(
            @PathVariable Long communityId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                communityMembershipService.listMembers(communityId, pageable, currentUser)));
    }

    @PostMapping("/members")
    public ResponseEntity<ApiResponse<CommunityMemberResponse>> join(
            @PathVariable Long communityId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(communityMembershipService.joinSelf(communityId, currentUser), "Joined community"));
    }

    @DeleteMapping("/members/me")
    public ResponseEntity<ApiResponse<Void>> leave(
            @PathVariable Long communityId,
            @AuthenticationPrincipal User currentUser) {
        communityMembershipService.leave(communityId, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Left community"));
    }

    @PutMapping("/members/{userId}/role")
    public ResponseEntity<ApiResponse<CommunityMemberResponse>> changeRole(
            @PathVariable Long communityId,
            @PathVariable Long userId,
            @Valid @RequestBody ChangeRoleRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                communityMembershipService.changeRole(communityId, userId, req.role(), currentUser), "Role updated"));
    }

    @DeleteMapping("/members/{userId}")
    public ResponseEntity<ApiResponse<Void>> kick(
            @PathVariable Long communityId,
            @PathVariable Long userId,
            @AuthenticationPrincipal User currentUser) {
        communityMembershipService.kick(communityId, userId, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Member removed"));
    }

    // ── Join requests (PRIVATE communities) ─────────────────────────────────

    @PostMapping("/join-requests")
    public ResponseEntity<ApiResponse<CommunityJoinRequestResponse>> requestToJoin(
            @PathVariable Long communityId,
            @Valid @RequestBody CreateJoinRequestRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                communityMembershipService.requestToJoin(communityId, req, currentUser), "Join request submitted"));
    }

    @GetMapping("/join-requests")
    public ResponseEntity<ApiResponse<Page<CommunityJoinRequestResponse>>> listJoinRequests(
            @PathVariable Long communityId,
            @RequestParam(required = false) JoinRequestStatus status,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                communityMembershipService.listJoinRequests(communityId, status, pageable, currentUser)));
    }

    @PostMapping("/join-requests/{requestId}/approve")
    public ResponseEntity<ApiResponse<CommunityJoinRequestResponse>> approveJoinRequest(
            @PathVariable Long communityId,
            @PathVariable Long requestId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                communityMembershipService.approveJoinRequest(communityId, requestId, currentUser), "Join request approved"));
    }

    @PostMapping("/join-requests/{requestId}/reject")
    public ResponseEntity<ApiResponse<CommunityJoinRequestResponse>> rejectJoinRequest(
            @PathVariable Long communityId,
            @PathVariable Long requestId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                communityMembershipService.rejectJoinRequest(communityId, requestId, currentUser), "Join request rejected"));
    }
}
