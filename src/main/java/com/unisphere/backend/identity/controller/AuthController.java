package com.unisphere.backend.identity.controller;

import com.unisphere.backend.common.ApiResponse;
import com.unisphere.backend.common.exception.TokenExpiredException;
import com.unisphere.backend.identity.dto.*;
import com.unisphere.backend.identity.service.AuthService;
import com.unisphere.backend.identity.util.RefreshTokenCookie;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
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
    private final RefreshTokenCookie refreshTokenCookie;

    /**
     * Every endpoint that mints tokens returns the access token in the body and the refresh token
     * in an HttpOnly cookie — never the other way round, and never both in the body.
     */
    private ResponseEntity<ApiResponse<AuthResponse>> withRefreshCookie(
            AuthResponse auth, HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.build(auth.refreshToken()))
                .body(message == null ? ApiResponse.ok(auth) : ApiResponse.ok(auth, message));
    }

    @Operation(summary = "Register a student account")
    @PostMapping("/register/student")
    public ResponseEntity<ApiResponse<AuthResponse>> registerStudent(
            @Valid @RequestBody RegisterStudentRequest request) {
        return withRefreshCookie(authService.registerStudent(request),
                HttpStatus.CREATED, "Student registered successfully");
    }

    @Operation(summary = "Register an alumni account")
    @PostMapping("/register/alumni")
    public ResponseEntity<ApiResponse<AuthResponse>> registerAlumni(
            @Valid @RequestBody RegisterAlumniRequest request) {
        return withRefreshCookie(authService.registerAlumni(request),
                HttpStatus.CREATED, "Alumni registered successfully");
    }

    @Operation(summary = "Register an employer account")
    @PostMapping("/register/employer")
    public ResponseEntity<ApiResponse<AuthResponse>> registerEmployer(
            @Valid @RequestBody RegisterEmployerRequest request) {
        return withRefreshCookie(authService.registerEmployer(request),
                HttpStatus.CREATED, "Employer registered successfully");
    }

    @Operation(summary = "Register a university account")
    @PostMapping("/register/university")
    public ResponseEntity<ApiResponse<AuthResponse>> registerUniversity(
            @Valid @RequestBody RegisterUniversityRequest request) {
        return withRefreshCookie(authService.registerUniversity(request),
                HttpStatus.CREATED, "University registered successfully");
    }

    @Operation(summary = "Register a club account")
    @PostMapping("/register/club")
    public ResponseEntity<ApiResponse<AuthResponse>> registerClub(
            @Valid @RequestBody RegisterClubRequest request) {
        return withRefreshCookie(authService.registerClub(request),
                HttpStatus.CREATED, "Club registered successfully");
    }

    @Operation(summary = "Login with email and password")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(authService.login(request), HttpStatus.OK, null);
    }

    @Operation(summary = "Get the current authenticated user's profile")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> me(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(authService.meByEmail(authentication.getName())));
    }

    @Operation(
            summary = "Issue a new access token using the refresh token cookie",
            description = "Reads the HttpOnly refresh_token cookie, rotates it, and returns a fresh access token. "
                    + "Also serves as the app's silent re-auth on page load."
    )
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new TokenExpiredException("No refresh token cookie present");
        }
        return withRefreshCookie(authService.refresh(refreshToken), HttpStatus.OK, null);
    }

    @Operation(
            summary = "Logout the current user",
            description = "Revokes the refresh token server-side and clears the cookie. "
                    + "The short-lived access token expires naturally."
    )
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken) {
        // Absent cookie is not an error — logging out of an already-dead session should still
        // succeed and still clear the cookie, so the client can always reach a signed-out state.
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.clear())
                .body(ApiResponse.ok(null, "Logged out successfully"));
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
