package com.unisphere.backend.campus.news.controller;

import com.unisphere.backend.campus.news.dto.request.CreateNewsCommentRequest;
import com.unisphere.backend.campus.news.dto.request.UpdateNewsCommentRequest;
import com.unisphere.backend.campus.news.dto.response.NewsCommentResponse;
import com.unisphere.backend.campus.news.dto.response.NewsLikeToggleResponse;
import com.unisphere.backend.campus.news.service.NewsCommentService;
import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.identity.entity.User;
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
@RequestMapping("/api/v1/news/{articleId}/comments")
@RequiredArgsConstructor
public class NewsCommentController {

    private final NewsCommentService newsCommentService;

    @PostMapping
    public ResponseEntity<ApiResponse<NewsCommentResponse>> createComment(
            @PathVariable Long articleId,
            @Valid @RequestBody CreateNewsCommentRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(newsCommentService.createComment(articleId, req, currentUser),
                        "Comment created"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<NewsCommentResponse>>> getComments(
            @PathVariable Long articleId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                newsCommentService.getTopLevelComments(articleId, pageable, currentUser)));
    }

    @GetMapping("/{commentId}/replies")
    public ResponseEntity<ApiResponse<Page<NewsCommentResponse>>> getReplies(
            @PathVariable Long commentId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                newsCommentService.getReplies(commentId, pageable, currentUser)));
    }

    @PutMapping("/{commentId}")
    public ResponseEntity<ApiResponse<NewsCommentResponse>> updateComment(
            @PathVariable Long commentId,
            @Valid @RequestBody UpdateNewsCommentRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                newsCommentService.updateComment(commentId, req, currentUser), "Comment updated"));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable Long commentId,
            @AuthenticationPrincipal User currentUser) {
        newsCommentService.deleteComment(commentId, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Comment deleted"));
    }

    @PostMapping("/{commentId}/like")
    public ResponseEntity<ApiResponse<NewsLikeToggleResponse>> toggleLike(
            @PathVariable Long commentId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(newsCommentService.toggleLike(commentId, currentUser)));
    }
}
