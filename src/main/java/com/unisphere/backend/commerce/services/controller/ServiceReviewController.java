package com.unisphere.backend.commerce.services.controller;

import com.unisphere.backend.commerce.services.dto.request.CreateServiceReviewRequest;
import com.unisphere.backend.commerce.services.dto.response.ServiceReviewResponse;
import com.unisphere.backend.commerce.services.service.ServiceReviewService;
import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.identity.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Service reviews", description = "Leave and browse ratings/reviews on completed service orders")
@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
public class ServiceReviewController {

    private final ServiceReviewService serviceReviewService;

    @Operation(summary = "Public reviews for a listing — client-authored reviews of the provider")
    @GetMapping("/{listingId}/reviews")
    public ResponseEntity<ApiResponse<Page<ServiceReviewResponse>>> listReviewsForListing(
            @PathVariable Long listingId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                serviceReviewService.listReviewsForListing(listingId, pageable, currentUser)));
    }

    @Operation(summary = "Leave a review on a completed order — client or provider, one per person per order")
    @PostMapping("/orders/{orderId}/reviews")
    public ResponseEntity<ApiResponse<ServiceReviewResponse>> createReview(
            @PathVariable Long orderId,
            @Valid @RequestBody CreateServiceReviewRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(serviceReviewService.createReview(orderId, req, currentUser), "Review submitted"));
    }

    @Operation(summary = "Both sides' reviews for one order")
    @GetMapping("/orders/{orderId}/reviews")
    public ResponseEntity<ApiResponse<List<ServiceReviewResponse>>> listReviewsForOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(serviceReviewService.listReviewsForOrder(orderId, currentUser)));
    }
}
