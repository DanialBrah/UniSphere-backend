package com.unisphere.backend.identity.controller;

import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.identity.dto.*;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ── Read ─────────────────────────────────────────────────────────────────

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMe(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getMe(currentUser)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getById(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getUserById(id)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<UserSummaryResponse>>> search(
            @RequestParam String q,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(userService.searchUsers(q, currentUser, pageable)));
    }

    // Admin: list all users with optional role / status filters
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<UserProfileResponse>>> listUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(userService.listUsers(role, status, pageable)));
    }

    // ── Update ───────────────────────────────────────────────────────────────

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateProfile(currentUser, req), "Profile updated"));
    }

    // Admin: change any user's status (ACTIVE, SUSPENDED, BANNED, PENDING)
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UserStatusUpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(userService.adminUpdateStatus(id, req), "Status updated"));
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteMe(
            @AuthenticationPrincipal User currentUser) {
        userService.deleteMe(currentUser);
        return ResponseEntity.ok(ApiResponse.ok(null, "Account deleted"));
    }

    // Admin: soft-delete any user
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable Long id) {
        userService.adminDeleteUser(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "User deleted"));
    }
}
