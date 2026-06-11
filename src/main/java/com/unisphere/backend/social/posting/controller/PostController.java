package com.unisphere.backend.social.posting.controller;

import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.social.posting.dto.request.CreatePostRequest;
import com.unisphere.backend.social.posting.dto.request.UpdatePostRequest;
import com.unisphere.backend.social.posting.dto.response.LikeToggleResponse;
import com.unisphere.backend.social.posting.dto.response.PostResponse;
import com.unisphere.backend.social.posting.dto.response.SaveToggleResponse;
import com.unisphere.backend.social.posting.service.PostService;
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
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    public ResponseEntity<ApiResponse<PostResponse>> createPost(
            @Valid @RequestBody CreatePostRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(postService.createPost(req, currentUser), "Post created"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PostResponse>>> getFeed(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(postService.getFeed(pageable, currentUser)));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<PostResponse>> getPost(
            @PathVariable Long postId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(postService.getPostById(postId, currentUser)));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<Page<PostResponse>>> getPostsByUser(
            @PathVariable Long userId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(postService.getPostsByUser(userId, pageable, currentUser)));
    }

    @PutMapping("/{postId}")
    public ResponseEntity<ApiResponse<PostResponse>> updatePost(
            @PathVariable Long postId,
            @Valid @RequestBody UpdatePostRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(postService.updatePost(postId, req, currentUser), "Post updated"));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @PathVariable Long postId,
            @AuthenticationPrincipal User currentUser) {
        postService.deletePost(postId, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Post deleted"));
    }

    @PostMapping("/{postId}/like")
    public ResponseEntity<ApiResponse<LikeToggleResponse>> toggleLike(
            @PathVariable Long postId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(postService.toggleLike(postId, currentUser)));
    }

    @PostMapping("/{postId}/save")
    public ResponseEntity<ApiResponse<SaveToggleResponse>> toggleSave(
            @PathVariable Long postId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(postService.toggleSave(postId, currentUser)));
    }

    @GetMapping("/liked")
    public ResponseEntity<ApiResponse<Page<PostResponse>>> getLikedPosts(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(postService.getLikedPosts(pageable, currentUser)));
    }

    @GetMapping("/saved")
    public ResponseEntity<ApiResponse<Page<PostResponse>>> getSavedPosts(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(postService.getSavedPosts(pageable, currentUser)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<PostResponse>>> search(
            @RequestParam String q,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(postService.searchPosts(q, pageable, currentUser)));
    }
}
