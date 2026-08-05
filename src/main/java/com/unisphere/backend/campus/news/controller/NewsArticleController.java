package com.unisphere.backend.campus.news.controller;

import com.unisphere.backend.campus.news.dto.request.CreateNewsArticleRequest;
import com.unisphere.backend.campus.news.dto.request.NewsStatusUpdateRequest;
import com.unisphere.backend.campus.news.dto.request.UpdateNewsArticleRequest;
import com.unisphere.backend.campus.news.dto.response.*;
import com.unisphere.backend.campus.news.enums.NewsCategory;
import com.unisphere.backend.campus.news.enums.NewsStatus;
import com.unisphere.backend.campus.news.service.NewsService;
import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.identity.entity.User;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
public class NewsArticleController {

    private final NewsService newsService;

    @PostMapping
    public ResponseEntity<ApiResponse<NewsArticleResponse>> createArticle(
            @Valid @RequestBody CreateNewsArticleRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(newsService.createArticle(req, currentUser), "Article created"));
    }

    /**
     * The published feed. {@code id} is the secondary sort so two articles published in the same
     * millisecond cannot swap places between pages.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<NewsArticleSummaryResponse>>> getFeed(
            @RequestParam(required = false) NewsCategory category,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Boolean featured,
            @PageableDefault(size = 20, sort = {"publishedAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                newsService.getFeed(category, tag, featured, pageable, currentUser)));
    }

    @GetMapping("/featured")
    public ResponseEntity<ApiResponse<Page<NewsArticleSummaryResponse>>> getFeatured(
            @PageableDefault(size = 20, sort = {"publishedAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                newsService.getFeed(null, null, true, pageable, currentUser)));
    }

    /**
     * FULLTEXT search over title and content.
     *
     * <p>No {@code sort} default on purpose — this reaches a native query that carries its own
     * ORDER BY, and Spring would append a raw property name that is not a real column. Note also
     * that MySQL's {@code innodb_ft_min_token_size} defaults to 3, so one- and two-character terms
     * match nothing regardless of the data.
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<NewsArticleSummaryResponse>>> search(
            @RequestParam String q,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(newsService.search(q, pageable, currentUser)));
    }

    @GetMapping("/tags")
    public ResponseEntity<ApiResponse<List<NewsTagCountResponse>>> getPopularTags(
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(newsService.getPopularTags(limit, currentUser)));
    }

    /** The caller's own articles, drafts included. */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Page<NewsArticleSummaryResponse>>> getMyArticles(
            @RequestParam(required = false) NewsStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(newsService.getMyArticles(status, pageable, currentUser)));
    }

    @GetMapping("/liked")
    public ResponseEntity<ApiResponse<Page<NewsArticleSummaryResponse>>> getLikedArticles(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(newsService.getLikedArticles(pageable, currentUser)));
    }

    @GetMapping("/saved")
    public ResponseEntity<ApiResponse<Page<NewsArticleSummaryResponse>>> getSavedArticles(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(newsService.getSavedArticles(pageable, currentUser)));
    }

    @GetMapping("/author/{authorId}")
    public ResponseEntity<ApiResponse<Page<NewsArticleSummaryResponse>>> getArticlesByAuthor(
            @PathVariable Long authorId,
            @RequestParam(required = false) NewsStatus status,
            @PageableDefault(size = 20, sort = {"publishedAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                newsService.getArticlesByAuthor(authorId, status, pageable, currentUser)));
    }

    @GetMapping("/{articleId}")
    public ResponseEntity<ApiResponse<NewsArticleResponse>> getArticle(
            @PathVariable Long articleId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(newsService.getArticleById(articleId, currentUser)));
    }

    @PutMapping("/{articleId}")
    public ResponseEntity<ApiResponse<NewsArticleResponse>> updateArticle(
            @PathVariable Long articleId,
            @Valid @RequestBody UpdateNewsArticleRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                newsService.updateArticle(articleId, req, currentUser), "Article updated"));
    }

    @PatchMapping("/{articleId}/status")
    public ResponseEntity<ApiResponse<NewsArticleResponse>> changeStatus(
            @PathVariable Long articleId,
            @Valid @RequestBody NewsStatusUpdateRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                newsService.changeStatus(articleId, req, currentUser), "Article status updated"));
    }

    @DeleteMapping("/{articleId}")
    public ResponseEntity<ApiResponse<Void>> deleteArticle(
            @PathVariable Long articleId,
            @AuthenticationPrincipal User currentUser) {
        newsService.deleteArticle(articleId, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Article deleted"));
    }

    @PostMapping("/{articleId}/like")
    public ResponseEntity<ApiResponse<NewsLikeToggleResponse>> toggleLike(
            @PathVariable Long articleId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(newsService.toggleLike(articleId, currentUser)));
    }

    @PostMapping("/{articleId}/save")
    public ResponseEntity<ApiResponse<NewsSaveToggleResponse>> toggleSave(
            @PathVariable Long articleId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(newsService.toggleSave(articleId, currentUser)));
    }
}
