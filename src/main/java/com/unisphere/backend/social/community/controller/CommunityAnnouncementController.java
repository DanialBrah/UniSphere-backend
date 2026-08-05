package com.unisphere.backend.social.community.controller;

import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.social.community.dto.request.CreateAnnouncementRequest;
import com.unisphere.backend.social.community.dto.request.UpdateAnnouncementRequest;
import com.unisphere.backend.social.community.dto.response.CommunityAnnouncementResponse;
import com.unisphere.backend.social.community.service.CommunityAnnouncementService;
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
@RequestMapping("/api/v1/communities/{communityId}/announcements")
@RequiredArgsConstructor
public class CommunityAnnouncementController {

    private final CommunityAnnouncementService communityAnnouncementService;

    @PostMapping
    public ResponseEntity<ApiResponse<CommunityAnnouncementResponse>> create(
            @PathVariable Long communityId,
            @Valid @RequestBody CreateAnnouncementRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                communityAnnouncementService.create(communityId, req, currentUser), "Announcement posted"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<CommunityAnnouncementResponse>>> list(
            @PathVariable Long communityId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                communityAnnouncementService.list(communityId, pageable, currentUser)));
    }

    @PutMapping("/{announcementId}")
    public ResponseEntity<ApiResponse<CommunityAnnouncementResponse>> update(
            @PathVariable Long communityId,
            @PathVariable Long announcementId,
            @Valid @RequestBody UpdateAnnouncementRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                communityAnnouncementService.update(communityId, announcementId, req, currentUser), "Announcement updated"));
    }

    @DeleteMapping("/{announcementId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long communityId,
            @PathVariable Long announcementId,
            @AuthenticationPrincipal User currentUser) {
        communityAnnouncementService.delete(communityId, announcementId, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Announcement deleted"));
    }
}
