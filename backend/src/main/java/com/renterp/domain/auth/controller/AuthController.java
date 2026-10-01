package com.renterp.domain.auth.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.domain.auth.dto.AuthResponse;
import com.renterp.domain.auth.dto.OtpChallengeResponse;
import com.renterp.domain.auth.dto.PasswordLoginRequest;
import com.renterp.domain.auth.dto.RefreshRequest;
import com.renterp.domain.auth.dto.RegisterRequest;
import com.renterp.domain.auth.dto.RequestOtpRequest;
import com.renterp.domain.auth.dto.UserResponse;
import com.renterp.domain.auth.dto.VerificationTokenResponse;
import com.renterp.domain.auth.dto.VerifyOtpRequest;
import com.renterp.domain.auth.security.AccessGuard;
import com.renterp.domain.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/v1/auth - sign-up, login, token refresh, logout, current user.
 * Everything here is public except logout and me (see SecurityConfig).
 * The request and response shapes match the app's AuthService.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AccessGuard accessGuard;

    public AuthController(AuthService authService, AccessGuard accessGuard) {
        this.authService = authService;
        this.accessGuard = accessGuard;
    }

    @PostMapping("/otp/request")
    public ResponseEntity<ApiResponse<OtpChallengeResponse>> requestOtp(
            @Valid @RequestBody RequestOtpRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Code sent", authService.requestOtp(request)));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<ApiResponse<VerificationTokenResponse>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Phone verified", authService.verifySignupOtp(request)));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account created", authService.register(request, userAgent)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> loginWithOtp(
            @Valid @RequestBody VerifyOtpRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return ResponseEntity.ok(ApiResponse.success("Logged in", authService.loginWithOtp(request, userAgent)));
    }

    @PostMapping("/login/password")
    public ResponseEntity<ApiResponse<AuthResponse>> loginWithPassword(
            @Valid @RequestBody PasswordLoginRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return ResponseEntity.ok(ApiResponse.success("Logged in",
                authService.loginWithPassword(request, userAgent)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Session refreshed",
                authService.refresh(request.getRefreshToken())));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        authService.logout(accessGuard.requireUser());
        return ResponseEntity.ok(ApiResponse.success("Logged out"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me() {
        return ResponseEntity.ok(ApiResponse.success("Current user", authService.me(accessGuard.requireUser())));
    }
}
