package com.renterp.domain.auth.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.domain.auth.dto.AuthResponse;
import com.renterp.domain.auth.dto.OtpChallengeResponse;
import com.renterp.domain.auth.dto.PasswordLoginRequest;
import com.renterp.domain.auth.dto.PasswordResetRequests;
import com.renterp.domain.auth.dto.RefreshRequest;
import com.renterp.domain.auth.dto.RegisterRequest;
import com.renterp.domain.auth.dto.RequestOtpRequest;
import com.renterp.domain.auth.dto.UserResponse;
import com.renterp.domain.auth.dto.VerificationTokenResponse;
import com.renterp.domain.auth.dto.VerifyOtpRequest;
import com.renterp.domain.auth.security.AccessGuard;
import com.renterp.domain.auth.service.AuthService;
import com.renterp.domain.auth.service.PasswordResetService;
import com.renterp.domain.tenancy.service.TenantProfileLinker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
 * /api/v1/auth - sign-up, login, forgotten password, token refresh, logout,
 * current user. Everything here is public except logout and me (see SecurityConfig).
 * The request and response shapes match the app's AuthService.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LogManager.getLogger(AuthController.class);

    private final AuthService authService;
    private final AccessGuard accessGuard;
    private final TenantProfileLinker profileLinker;
    private final PasswordResetService passwordReset;

    public AuthController(AuthService authService, AccessGuard accessGuard, TenantProfileLinker profileLinker,
                          PasswordResetService passwordReset) {
        this.authService = authService;
        this.accessGuard = accessGuard;
        this.profileLinker = profileLinker;
        this.passwordReset = passwordReset;
    }

    /**
     * C8: after the account is saved (its own transaction has committed), link
     * any tenant profile an owner created for this phone. Never fails the
     * login - a problem here is logged and retried on the next login.
     */
    private AuthResponse linked(AuthResponse response) {
        try {
            profileLinker.linkForUser(response.user().getId(), response.user().getPhone());
        } catch (RuntimeException e) {
            log.error("Linking tenant profiles failed — user: {}", response.user().getId(), e);
        }
        return response;
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
                .body(ApiResponse.success("Account created", linked(authService.register(request, userAgent))));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> loginWithOtp(
            @Valid @RequestBody VerifyOtpRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return ResponseEntity.ok(ApiResponse.success("Logged in", linked(authService.loginWithOtp(request, userAgent))));
    }

    @PostMapping("/login/password")
    public ResponseEntity<ApiResponse<AuthResponse>> loginWithPassword(
            @Valid @RequestBody PasswordLoginRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return ResponseEntity.ok(ApiResponse.success("Logged in",
                linked(authService.loginWithPassword(request, userAgent))));
    }

    /** Forgotten password, step 1: a code to the account's email or phone. */
    @PostMapping("/password/forgot")
    public ResponseEntity<ApiResponse<OtpChallengeResponse>> forgotPassword(
            @Valid @RequestBody PasswordResetRequests.Forgot request) {
        return ResponseEntity.ok(ApiResponse.success(
                "If an account uses this address, a code was sent to it", passwordReset.requestCode(request)));
    }

    /** Forgotten password, step 2: code + new password; signs out every device and logs this one in. */
    @PostMapping("/password/reset")
    public ResponseEntity<ApiResponse<AuthResponse>> resetPassword(
            @Valid @RequestBody PasswordResetRequests.Reset request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return ResponseEntity.ok(ApiResponse.success("Password reset",
                linked(passwordReset.reset(request, userAgent))));
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
