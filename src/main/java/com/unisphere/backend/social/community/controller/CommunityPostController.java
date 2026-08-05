package com.unisphere.backend.social.community.controller;

import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.social.community.service.CommunityPostService;
import com.unisphere.backend.social.posting.dto.request.CreatePostRequest;
import com.unisphere.backend.social.posting.dto.response.PostResponse;
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
@RequestMapping("/api/v1/communities/{communityId}/posts")
@RequiredArgsConstructor
public class CommunityPostController {

    private final CommunityPostService communityPostService;

    @PostMapping
    public ResponseEntity<ApiResponse<PostResponse>> createPost(
            @PathVariable Long communityId,
            @Valid @RequestBody CreatePostRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                communityPostService.createPost(communityId, req, currentUser), "Post created"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PostResponse>>> getFeed(
            @PathVariable Long communityId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(communityPostService.getFeed(communityId, pageable, currentUser)));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<ApiResponse<Void>> removePost(
            @PathVariable Long communityId,
            @PathVariable Long postId,
            @AuthenticationPrincipal User currentUser) {
        communityPostService.removePost(communityId, postId, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Post removed"));
    }
}
