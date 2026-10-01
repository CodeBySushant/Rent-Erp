package com.renterp.domain.tenancy.controller;

import com.renterp.domain.auth.security.ResourceAccess;
import com.renterp.common.response.ApiResponse;
import com.renterp.common.response.PagedResponse;
import com.renterp.domain.tenancy.dto.*;
import com.renterp.domain.tenancy.service.TenantProfileService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenant-profiles")
public class TenantProfileController {

    private final TenantProfileService service;

    private final ResourceAccess access;

    public TenantProfileController(ResourceAccess access,
            TenantProfileService service) {
        this.access = access; this.service = service; }

    @PostMapping
    public ResponseEntity<ApiResponse<TenantProfileResponse>> create(@Valid @RequestBody CreateTenantProfileRequest req) {
        access.createTenantProfile(req.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tenant profile created", service.createProfile(req)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TenantProfileResponse>> get(@PathVariable UUID id) {
        access.tenantProfile(id, ResourceAccess.Level.READ);
        return ResponseEntity.ok(ApiResponse.success("Tenant profile fetched", service.getProfileById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<TenantProfileResponse>>> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<TenantProfileResponse> page = service.listProfiles(pageable);
        return ResponseEntity.ok(ApiResponse.success("Tenant profiles fetched", PagedResponse.from(page)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TenantProfileResponse>> update(@PathVariable UUID id,
                                                                      @Valid @RequestBody UpdateTenantProfileRequest req) {
        access.tenantProfile(id, ResourceAccess.Level.WRITE);
        return ResponseEntity.ok(ApiResponse.success("Tenant profile updated", service.updateProfile(id, req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        access.tenantProfile(id, ResourceAccess.Level.WRITE);
        service.deleteProfile(id);
        return ResponseEntity.ok(ApiResponse.success("Tenant profile soft-deleted"));
    }

    // ── KYC sub-resource ──────────────────────────────────────────────────

    @PostMapping("/{id}/kyc")
    public ResponseEntity<ApiResponse<TenantKycResponse>> submitKyc(@PathVariable UUID id,
                                                                     @Valid @RequestBody SubmitKycRequest req) {
        access.tenantProfile(id, ResourceAccess.Level.WRITE);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("KYC submitted", service.submitKyc(id, req)));
    }

    @GetMapping("/{id}/kyc")
    public ResponseEntity<ApiResponse<TenantKycResponse>> getKyc(@PathVariable UUID id) {
        access.tenantProfile(id, ResourceAccess.Level.READ);
        return ResponseEntity.ok(ApiResponse.success("KYC fetched", service.getKyc(id)));
    }

    @PostMapping("/{id}/kyc/approve")
    public ResponseEntity<ApiResponse<TenantKycResponse>> approveKyc(@PathVariable UUID id) {
        access.tenantProfileDecide(id);
        return ResponseEntity.ok(ApiResponse.success("KYC approved", service.approveKyc(id)));
    }

    @PostMapping("/{id}/kyc/reject")
    public ResponseEntity<ApiResponse<TenantKycResponse>> rejectKyc(@PathVariable UUID id,
                                                                     @Valid @RequestBody KycDecisionRequest req) {
        access.tenantProfileDecide(id);
        return ResponseEntity.ok(ApiResponse.success("KYC rejected", service.rejectKyc(id, req)));
    }

    @PostMapping("/{id}/kyc/flag")
    public ResponseEntity<ApiResponse<TenantKycResponse>> flagKyc(@PathVariable UUID id,
                                                                   @Valid @RequestBody KycDecisionRequest req) {
        access.tenantProfileDecide(id);
        return ResponseEntity.ok(ApiResponse.success("KYC flagged for admin review", service.flagKyc(id, req)));
    }
}
