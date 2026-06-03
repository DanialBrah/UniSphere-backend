package com.unisphere.backend.identity.controller;

import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.identity.dto.*;
import com.unisphere.backend.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, token refresh and profile")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register a student account")
    @PostMapping("/register/student")
    public ResponseEntity<ApiResponse<AuthResponse>> registerStudent(
            @Valid @RequestBody RegisterStudentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(authService.registerStudent(request), "Student registered successfully"));
    }

    @Operation(summary = "Register an alumni account")
    @PostMapping("/register/alumni")
    public ResponseEntity<ApiResponse<AuthResponse>> registerAlumni(
            @Valid @RequestBody RegisterAlumniRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(authService.registerAlumni(request), "Alumni registered successfully"));
    }

    @Operation(summary = "Register an employer account")
    @PostMapping("/register/employer")
    public ResponseEntity<ApiResponse<AuthResponse>> registerEmployer(
            @Valid @RequestBody RegisterEmployerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(authService.registerEmployer(request), "Employer registered successfully"));
    }

    @Operation(summary = "Register a university account")
    @PostMapping("/register/university")
    public ResponseEntity<ApiResponse<AuthResponse>> registerUniversity(
            @Valid @RequestBody RegisterUniversityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(authService.registerUniversity(request), "University registered successfully"));
    }

    @Operation(summary = "Register a club account")
    @PostMapping("/register/club")
    public ResponseEntity<ApiResponse<AuthResponse>> registerClub(
            @Valid @RequestBody RegisterClubRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(authService.registerClub(request), "Club registered successfully"));
    }

    @Operation(summary = "Login with email and password")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
    }

    @Operation(summary = "Get the current authenticated user's profile")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> me(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(authService.meByEmail(authentication.getName())));
    }

    @Operation(summary = "Issue a new access token using a valid refresh token")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(request)));
    }

    @Operation(
            summary = "Logout the current user",
            description = "Revokes the provided refresh token server-side. The short-lived access token expires naturally."
    )
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.ok(null, "Logged out successfully"));
    }

    @Operation(
            summary = "Request a password reset email",
            description = "Sends a reset link to the address if it is registered. Always returns 200 to prevent email enumeration."
    )
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ResponseEntity.ok(ApiResponse.ok(null, "If that email is registered, a reset link has been sent"));
    }

    @Operation(
            summary = "Reset password using a reset token",
            description = "Validates the one-time token from the reset email and updates the user's password."
    )
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.ok(null, "Password reset successfully"));
    }
}
