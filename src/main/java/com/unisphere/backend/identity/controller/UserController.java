package com.unisphere.backend.identity.controller;

import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.identity.dto.UpdateProfileRequest;
import com.unisphere.backend.identity.dto.UserProfileResponse;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest req,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateProfile(currentUser, req), "Profile updated"));
    }
}
