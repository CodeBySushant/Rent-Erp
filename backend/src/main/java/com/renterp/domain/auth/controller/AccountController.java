package com.renterp.domain.auth.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.domain.auth.dto.AccountRequests;
import com.renterp.domain.auth.dto.OtpChallengeResponse;
import com.renterp.domain.auth.dto.SessionResponse;
import com.renterp.domain.auth.dto.UserResponse;
import com.renterp.domain.auth.service.AccountService;
import com.renterp.domain.auth.service.ProfilePhotoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The signed-in user's own account: password, phone, email, photo, devices, delete. */
@RestController
@RequestMapping("/api/v1/me")
public class AccountController {

    private final AccountService service;
    private final ProfilePhotoService photos;

    public AccountController(AccountService service, ProfilePhotoService photos) {
        this.service = service;
        this.photos = photos;
    }

    @PostMapping("/password")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> changePassword(
            @Valid @RequestBody AccountRequests.ChangePassword req) {
        int ended = service.changePassword(req);
        return ResponseEntity.ok(ApiResponse.success("Password changed", Map.of("otherDevicesSignedOut", ended)));
    }

    @PostMapping("/phone/otp")
    public ResponseEntity<ApiResponse<OtpChallengeResponse>> requestPhone(@Valid @RequestBody AccountRequests.NewPhone req) {
        return ResponseEntity.ok(ApiResponse.success("Code sent to the new number", service.requestPhoneChange(req)));
    }

    @PostMapping("/phone")
    public ResponseEntity<ApiResponse<UserResponse>> confirmPhone(@Valid @RequestBody AccountRequests.ConfirmCode req) {
        return ResponseEntity.ok(ApiResponse.success("Phone number changed", service.confirmPhoneChange(req)));
    }

    @PostMapping("/email/otp")
    public ResponseEntity<ApiResponse<OtpChallengeResponse>> requestEmail(@Valid @RequestBody AccountRequests.NewEmail req) {
        return ResponseEntity.ok(ApiResponse.success("Code sent to the new email", service.requestEmailChange(req)));
    }

    @PostMapping("/email")
    public ResponseEntity<ApiResponse<UserResponse>> confirmEmail(@Valid @RequestBody AccountRequests.ConfirmCode req) {
        return ResponseEntity.ok(ApiResponse.success("Email changed", service.confirmEmailChange(req)));
    }

    // ── PUT /api/v1/me/photo {fileId} — a PROFILE_PHOTO upload of the caller ──
    @PutMapping("/photo")
    public ResponseEntity<ApiResponse<UserResponse>> setPhoto(@Valid @RequestBody AccountRequests.SetPhoto req) {
        return ResponseEntity.ok(ApiResponse.success("Profile photo updated", photos.setMine(req.getFileId())));
    }

    // ── DELETE /api/v1/me/photo ────────────────────────────────────────────
    @DeleteMapping("/photo")
    public ResponseEntity<ApiResponse<UserResponse>> removePhoto() {
        return ResponseEntity.ok(ApiResponse.success("Profile photo removed", photos.removeMine()));
    }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> sessions() {
        return ResponseEntity.ok(ApiResponse.success("Devices fetched", service.sessions()));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> endSession(@PathVariable UUID sessionId) {
        service.endSession(sessionId);
        return ResponseEntity.ok(ApiResponse.success("Device signed out", null));
    }

    @PostMapping("/sessions/end-others")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> endOthers() {
        return ResponseEntity.ok(ApiResponse.success("Other devices signed out", Map.of("ended", service.endOtherSessions())));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> delete(@Valid @RequestBody AccountRequests.DeleteAccount req) {
        service.deleteAccount(req);
        return ResponseEntity.ok(ApiResponse.success("Account deleted", null));
    }
}
