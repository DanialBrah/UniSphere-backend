package com.unisphere.backend.commerce.services.controller;

import com.unisphere.backend.commerce.services.dto.request.CreateServiceListingRequest;
import com.unisphere.backend.commerce.services.dto.request.ServiceListingStatusUpdateRequest;
import com.unisphere.backend.commerce.services.dto.request.UpdateServiceListingRequest;
import com.unisphere.backend.commerce.services.dto.response.ServiceListingResponse;
import com.unisphere.backend.commerce.services.dto.response.ServiceListingStatsResponse;
import com.unisphere.backend.commerce.services.dto.response.ServiceListingSummaryResponse;
import com.unisphere.backend.commerce.services.enums.ServiceDeliveryMode;
import com.unisphere.backend.commerce.services.enums.ServiceListingStatus;
import com.unisphere.backend.commerce.services.enums.ServicePricingType;
import com.unisphere.backend.commerce.services.service.ServiceListingService;
import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.social.messaging.dto.response.ConversationResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Services", description = "Create, browse and manage service listings — STUDENT/ALUMNI/CLUB posting, everyone-but-admin ordering")
@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
public class ServiceListingController {

    private final ServiceListingService serviceListingService;

    @Operation(summary = "Create a service listing — STUDENT/ALUMNI/CLUB only, always starts ACTIVE")
    @PostMapping
    public ResponseEntity<ApiResponse<ServiceListingResponse>> createListing(
            @Valid @RequestBody CreateServiceListingRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(serviceListingService.createListing(req, currentUser), "Service listing created"));
    }

    /**
     * Browse. A null {@code status} defaults to ACTIVE — PAUSED is rejected outright, see
     * {@code ServiceListingService}.
     */
    @Operation(summary = "Browse listings, filtered by category, pricing type, delivery mode, university and status")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ServiceListingSummaryResponse>>> getFeed(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) ServicePricingType pricingType,
            @RequestParam(required = false) ServiceDeliveryMode deliveryMode,
            @RequestParam(required = false) Long universityId,
            @RequestParam(required = false) ServiceListingStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                serviceListingService.getFeed(category, pricingType, deliveryMode, universityId, status, pageable, currentUser)));
    }

    @Operation(summary = "Full-text search over listing titles and descriptions")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<ServiceListingSummaryResponse>>> search(
            @RequestParam String q,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(serviceListingService.search(q, pageable, currentUser)));
    }

    @Operation(summary = "The caller's own listings, every status including PAUSED")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Page<ServiceListingSummaryResponse>>> getMyListings(
            @RequestParam(required = false) ServiceListingStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(serviceListingService.getMyListings(status, pageable, currentUser)));
    }

    @Operation(summary = "Get one listing. A PAUSED listing is masked as 404 unless you're the provider/ADMIN")
    @GetMapping("/{listingId}")
    public ResponseEntity<ApiResponse<ServiceListingResponse>> getListing(
            @PathVariable Long listingId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(serviceListingService.getListingById(listingId, currentUser)));
    }

    @Operation(summary = "Update a listing you provide")
    @PutMapping("/{listingId}")
    public ResponseEntity<ApiResponse<ServiceListingResponse>> updateListing(
            @PathVariable Long listingId,
            @Valid @RequestBody UpdateServiceListingRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(serviceListingService.updateListing(listingId, req, currentUser), "Listing updated"));
    }

    @Operation(summary = "Pause or resume a listing")
    @PatchMapping("/{listingId}/status")
    public ResponseEntity<ApiResponse<ServiceListingResponse>> changeStatus(
            @PathVariable Long listingId,
            @Valid @RequestBody ServiceListingStatusUpdateRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(serviceListingService.changeStatus(listingId, req, currentUser), "Listing status updated"));
    }

    @Operation(summary = "Delete a listing you provide — blocked once it has orders in progress")
    @DeleteMapping("/{listingId}")
    public ResponseEntity<ApiResponse<Void>> deleteListing(
            @PathVariable Long listingId,
            @AuthenticationPrincipal User currentUser) {
        serviceListingService.deleteListing(listingId, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Listing deleted"));
    }

    @Operation(summary = "Order status breakdown for a listing you provide")
    @GetMapping("/{listingId}/stats")
    public ResponseEntity<ApiResponse<ServiceListingStatsResponse>> getStats(
            @PathVariable Long listingId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(serviceListingService.getStats(listingId, currentUser)));
    }

    @Operation(summary = "Message the provider — finds or creates a DIRECT conversation, no order required")
    @PostMapping("/{listingId}/inquire")
    public ResponseEntity<ApiResponse<ConversationResponse>> inquire(
            @PathVariable Long listingId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(serviceListingService.inquire(listingId, currentUser)));
    }
}
