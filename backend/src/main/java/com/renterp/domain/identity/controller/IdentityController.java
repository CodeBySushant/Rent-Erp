package com.renterp.domain.identity.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.domain.identity.dto.AdminOwnerIdentityResponse;
import com.renterp.domain.identity.dto.IdentityResponse;
import com.renterp.domain.identity.dto.SubmitIdentityRequest;
import com.renterp.domain.identity.service.IdentityService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** "Verify identity" for the signed-in user (owner or tenant), plus a developer view. */
@RestController
public class IdentityController {

    private final IdentityService service;

    public IdentityController(IdentityService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/me/identity")
    public ResponseEntity<ApiResponse<IdentityResponse>> mine() {
        return ResponseEntity.ok(ApiResponse.success("Identity fetched", service.mine()));
    }

    @PutMapping("/api/v1/me/identity")
    public ResponseEntity<ApiResponse<IdentityResponse>> submit(@Valid @RequestBody SubmitIdentityRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Identity saved", service.submit(req)));
    }

    /**
     * ADMIN only (H6): tenant identity submissions with full ID numbers and photo
     * file ids. ?status=PENDING lists the ones waiting for review.
     */
    @GetMapping("/api/v1/admin/tenant-kyc")
    public ResponseEntity<ApiResponse<List<com.renterp.domain.identity.dto.AdminTenantKycResponse>>> adminTenantKyc(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success("Tenant identities fetched",
                service.adminTenantKyc(status, page, size)));
    }

    /** ADMIN only: stored owner identities with full ID numbers. */
    @GetMapping("/api/v1/admin/owner-identities")
    public ResponseEntity<ApiResponse<List<AdminOwnerIdentityResponse>>> adminList(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success("Owner identities fetched", service.adminList(page, size)));
    }
}
