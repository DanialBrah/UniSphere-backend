package com.unisphere.backend.campus.lostfound.controller;

import com.unisphere.backend.campus.lostfound.dto.request.CreateLostFoundItemRequest;
import com.unisphere.backend.campus.lostfound.dto.request.LostFoundStatusUpdateRequest;
import com.unisphere.backend.campus.lostfound.dto.request.UpdateLostFoundItemRequest;
import com.unisphere.backend.campus.lostfound.dto.response.*;
import com.unisphere.backend.campus.lostfound.enums.LostFoundCategory;
import com.unisphere.backend.campus.lostfound.enums.LostFoundItemStatus;
import com.unisphere.backend.campus.lostfound.enums.LostFoundItemType;
import com.unisphere.backend.campus.lostfound.service.LostFoundMatchService;
import com.unisphere.backend.campus.lostfound.service.LostFoundService;
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
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Base path is the module root rather than {@code /items} so {@code /stats} can live here without a
 * fourth controller — the same arrangement {@code CommunityMembershipController} uses.
 */
@Tag(name = "Lost & Found", description = "Report lost and found items, browse the campus board and map")
@RestController
@RequestMapping("/api/v1/lost-found")
@RequiredArgsConstructor
public class LostFoundItemController {

    private final LostFoundService lostFoundService;
    private final LostFoundMatchService lostFoundMatchService;

    @Operation(summary = "Report a lost or found item")
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<LostFoundItemResponse>> createItem(
            @Valid @RequestBody CreateLostFoundItemRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(lostFoundService.createItem(req, currentUser), "Item reported"));
    }

    @Operation(summary = "Browse the board, filtered by type, status and category")
    @GetMapping("/items")
    public ResponseEntity<ApiResponse<Page<LostFoundItemSummaryResponse>>> getFeed(
            @RequestParam(required = false) LostFoundItemType type,
            @RequestParam(required = false) LostFoundItemStatus status,
            @RequestParam(required = false) LostFoundCategory category,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                lostFoundService.getFeed(type, status, category, pageable, currentUser)));
    }

    /**
     * FULLTEXT search over title and description.
     *
     * <p>No {@code sort} default on purpose — this reaches a native query that carries its own
     * ORDER BY, and Spring would append a raw property name that is not a real column. Note also
     * that MySQL's {@code innodb_ft_min_token_size} defaults to 3, so one- and two-character terms
     * match nothing regardless of the data.
     */
    @Operation(summary = "Full-text search over item titles and descriptions")
    @GetMapping("/items/search")
    public ResponseEntity<ApiResponse<Page<LostFoundItemSummaryResponse>>> search(
            @RequestParam String q,
            @RequestParam(required = false) LostFoundItemType type,
            @RequestParam(required = false) LostFoundItemStatus status,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                lostFoundService.search(q, type, status, pageable, currentUser)));
    }

    /**
     * Items within {@code radiusKm} of a point, nearest first.
     *
     * <p>Same no-{@code sort} rule as {@link #search} — the query is native and orders by distance.
     */
    @Operation(summary = "Find items near a coordinate, ordered by distance")
    @GetMapping("/items/nearby")
    public ResponseEntity<ApiResponse<Page<LostFoundNearbyItemResponse>>> getNearby(
            @RequestParam BigDecimal lat,
            @RequestParam BigDecimal lng,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) LostFoundItemType type,
            @RequestParam(required = false) LostFoundItemStatus status,
            @RequestParam(required = false) LostFoundCategory category,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                lostFoundService.getNearby(lat, lng, radiusKm, type, status, category, pageable, currentUser)));
    }

    /**
     * Map pins inside a viewport. Hard-capped and unpaginated: a map client re-queries on pan
     * rather than paging, so a count query would be pure waste.
     */
    @Operation(summary = "Map pins within a bounding-box viewport")
    @GetMapping("/items/map")
    public ResponseEntity<ApiResponse<List<LostFoundMapPinResponse>>> getMapPins(
            @RequestParam BigDecimal minLat,
            @RequestParam BigDecimal maxLat,
            @RequestParam BigDecimal minLng,
            @RequestParam BigDecimal maxLng,
            @RequestParam(required = false) LostFoundItemType type,
            @RequestParam(required = false) LostFoundItemStatus status,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                lostFoundService.getMapPins(minLat, maxLat, minLng, maxLng, type, status, currentUser)));
    }

    @Operation(summary = "The caller's own reports, every status")
    @GetMapping("/items/me")
    public ResponseEntity<ApiResponse<Page<LostFoundItemSummaryResponse>>> getMyItems(
            @RequestParam(required = false) LostFoundItemStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(lostFoundService.getMyItems(status, pageable, currentUser)));
    }

    @Operation(summary = "Board-wide counts by type, status and category")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<LostFoundStatsResponse>> getStats(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(lostFoundService.getStats(currentUser)));
    }

    @Operation(summary = "Get one item. Location detail is masked for viewers without an approved claim")
    @GetMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<LostFoundItemResponse>> getItem(
            @PathVariable Long itemId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(lostFoundService.getItemById(itemId, currentUser)));
    }

    @Operation(summary = "Update an item you reported")
    @PutMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<LostFoundItemResponse>> updateItem(
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateLostFoundItemRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                lostFoundService.updateItem(itemId, req, currentUser), "Item updated"));
    }

    @Operation(summary = "Resolve, cancel or relist an item")
    @PatchMapping("/items/{itemId}/status")
    public ResponseEntity<ApiResponse<LostFoundItemResponse>> changeStatus(
            @PathVariable Long itemId,
            @Valid @RequestBody LostFoundStatusUpdateRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                lostFoundService.changeStatus(itemId, req, currentUser), "Item status updated"));
    }

    @Operation(summary = "Delete an item you reported")
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<Void>> deleteItem(
            @PathVariable Long itemId,
            @AuthenticationPrincipal User currentUser) {
        lostFoundService.deleteItem(itemId, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Item deleted"));
    }

    /**
     * Suggested counterparts, scored. Restricted to the reporter and admins: a list of FOUND items
     * that plausibly match a stranger's missing wallet is a fraud roadmap.
     */
    @Operation(summary = "Suggested counterpart matches for your own report")
    @GetMapping("/items/{itemId}/matches")
    public ResponseEntity<ApiResponse<List<LostFoundMatchResponse>>> getMatches(
            @PathVariable Long itemId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(lostFoundMatchService.findMatches(itemId, currentUser)));
    }
}
