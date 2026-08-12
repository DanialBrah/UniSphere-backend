package com.unisphere.backend.campus.lostfound.controller;

import com.unisphere.backend.campus.lostfound.dto.request.CreateLostFoundClaimRequest;
import com.unisphere.backend.campus.lostfound.dto.request.LostFoundClaimDecisionRequest;
import com.unisphere.backend.campus.lostfound.dto.response.LostFoundClaimResponse;
import com.unisphere.backend.campus.lostfound.enums.LostFoundClaimStatus;
import com.unisphere.backend.campus.lostfound.service.LostFoundClaimService;
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

/**
 * Claims against reported items.
 *
 * <p>Collection operations nest under the item because they need it for authorization anyway;
 * single-claim operations are flat because a claim id is globally unique and requiring the item id
 * would add a redundant consistency check. Same split as GitHub's
 * {@code POST /repos/{o}/{r}/issues/{n}/comments} vs {@code PATCH /repos/{o}/{r}/issues/comments/{id}}.
 *
 * <p>There is no {@code DELETE} — claims are audit history, and withdrawal is a
 * {@code PATCH} to {@code CANCELLED}.
 */
@Tag(name = "Lost & Found claims", description = "Claim a reported item and adjudicate claims on your own")
@RestController
@RequestMapping("/api/v1/lost-found")
@RequiredArgsConstructor
public class LostFoundClaimController {

    private final LostFoundClaimService lostFoundClaimService;

    @Operation(summary = "Claim an item someone else reported, with proof of ownership")
    @PostMapping("/items/{itemId}/claims")
    public ResponseEntity<ApiResponse<LostFoundClaimResponse>> submitClaim(
            @PathVariable Long itemId,
            @Valid @RequestBody CreateLostFoundClaimRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(lostFoundClaimService.submitClaim(itemId, req, currentUser),
                        "Claim submitted"));
    }

    @Operation(summary = "List claims on an item. Reporters see all; claimants see only their own")
    @GetMapping("/items/{itemId}/claims")
    public ResponseEntity<ApiResponse<Page<LostFoundClaimResponse>>> listClaimsForItem(
            @PathVariable Long itemId,
            @RequestParam(required = false) LostFoundClaimStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                lostFoundClaimService.listClaimsForItem(itemId, status, pageable, currentUser)));
    }

    @Operation(summary = "Claims the caller has submitted, across every item")
    @GetMapping("/claims/me")
    public ResponseEntity<ApiResponse<Page<LostFoundClaimResponse>>> listMyClaims(
            @RequestParam(required = false) LostFoundClaimStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                lostFoundClaimService.listMyClaims(status, pageable, currentUser)));
    }

    @Operation(summary = "Get one claim — claimant, item reporter or admin only")
    @GetMapping("/claims/{claimId}")
    public ResponseEntity<ApiResponse<LostFoundClaimResponse>> getClaim(
            @PathVariable Long claimId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(lostFoundClaimService.getClaim(claimId, currentUser)));
    }

    /**
     * Approve, reject or cancel. One PATCH with a status field rather than the community module's
     * {@code POST .../approve} + {@code POST .../reject}: those are RPC verbs on a resource path,
     * and a single PATCH makes CANCELLED a first-class third outcome instead of a third endpoint.
     */
    @Operation(summary = "Approve or reject a claim (reporter), or cancel your own (claimant)")
    @PatchMapping("/claims/{claimId}")
    public ResponseEntity<ApiResponse<LostFoundClaimResponse>> decideClaim(
            @PathVariable Long claimId,
            @Valid @RequestBody LostFoundClaimDecisionRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                lostFoundClaimService.decideClaim(claimId, req, currentUser), "Claim updated"));
    }
}
