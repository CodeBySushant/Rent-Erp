package com.renterp.domain.tenancy.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.domain.tenancy.dto.BlockTenantRequest;
import com.renterp.domain.tenancy.dto.BlockedTenantResponse;
import com.renterp.domain.tenancy.service.BlockedTenantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Property-scoped surface — matches spec §14.3 T3 "property-scoped only, unblock-able".
@RestController
@RequestMapping("/api/v1/properties/{propertyId}/blocked-tenants")
public class BlockedTenantController {

    private final BlockedTenantService service;

    public BlockedTenantController(BlockedTenantService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<ApiResponse<BlockedTenantResponse>> block(@PathVariable UUID propertyId,
                                                                     @Valid @RequestBody BlockTenantRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tenant blocked", service.block(propertyId, req)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BlockedTenantResponse>>> list(@PathVariable UUID propertyId) {
        return ResponseEntity.ok(ApiResponse.success("Blocked tenants fetched", service.listActive(propertyId)));
    }

    @DeleteMapping("/{tenantProfileId}")
    public ResponseEntity<ApiResponse<Void>> unblock(@PathVariable UUID propertyId,
                                                      @PathVariable UUID tenantProfileId) {
        service.unblock(propertyId, tenantProfileId);
        return ResponseEntity.ok(ApiResponse.success("Tenant unblocked"));
    }
}
