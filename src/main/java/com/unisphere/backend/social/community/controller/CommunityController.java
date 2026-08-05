package com.unisphere.backend.social.community.controller;

import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.social.community.dto.request.CreateCommunityRequest;
import com.unisphere.backend.social.community.dto.request.UpdateCommunityRequest;
import com.unisphere.backend.social.community.dto.response.ChatAccessResponse;
import com.unisphere.backend.social.community.dto.response.CommunityResponse;
import com.unisphere.backend.social.community.service.CommunityChatService;
import com.unisphere.backend.social.community.service.CommunityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/communities")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;
    private final CommunityChatService communityChatService;

    @PostMapping
    public ResponseEntity<ApiResponse<CommunityResponse>> createCommunity(
            @Valid @RequestBody CreateCommunityRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(communityService.createCommunity(req, currentUser), "Community created"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<CommunityResponse>>> discover(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(communityService.listDiscoverable(pageable, currentUser)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<CommunityResponse>>> search(
            @RequestParam String q,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(communityService.search(q, pageable, currentUser)));
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<Page<CommunityResponse>>> mine(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(communityService.listMine(pageable, currentUser)));
    }

    @GetMapping("/{communityId}")
    public ResponseEntity<ApiResponse<CommunityResponse>> getCommunity(
            @PathVariable Long communityId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(communityService.getCommunity(communityId, currentUser)));
    }

    @PutMapping("/{communityId}")
    public ResponseEntity<ApiResponse<CommunityResponse>> updateCommunity(
            @PathVariable Long communityId,
            @Valid @RequestBody UpdateCommunityRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                communityService.updateCommunity(communityId, req, currentUser), "Community updated"));
    }

    @DeleteMapping("/{communityId}")
    public ResponseEntity<ApiResponse<Void>> deleteCommunity(
            @PathVariable Long communityId,
            @AuthenticationPrincipal User currentUser) {
        communityService.deleteCommunity(communityId, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Community deleted"));
    }

    @GetMapping("/{communityId}/chat")
    public ResponseEntity<ApiResponse<ChatAccessResponse>> getChatAccess(
            @PathVariable Long communityId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(communityChatService.getChatAccess(communityId, currentUser)));
    }
}
