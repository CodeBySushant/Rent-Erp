package com.renterp.domain.request.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.domain.auth.security.ResourceAccess;
import com.renterp.domain.request.dto.CreateRequestRequest;
import com.renterp.domain.request.dto.DecideRequestRequest;
import com.renterp.domain.request.dto.RequestResponse;
import com.renterp.domain.request.entity.TenantRequest.Status;
import com.renterp.domain.request.service.TenantRequestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Tenant requests: room change, vacate, maintenance, other. */
@RestController
public class TenantRequestController {

    private final TenantRequestService service;
    private final ResourceAccess access;

    public TenantRequestController(TenantRequestService service, ResourceAccess access) {
        this.service = service;
        this.access = access;
    }

    @PostMapping("/api/v1/memberships/{membershipId}/requests")
    public ResponseEntity<ApiResponse<RequestResponse>> create(@PathVariable UUID membershipId,
                                                               @Valid @RequestBody CreateRequestRequest req) {
        access.membershipSelfOrManager(membershipId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Request sent", service.create(membershipId, req)));
    }

    @GetMapping("/api/v1/memberships/{membershipId}/requests")
    public ResponseEntity<ApiResponse<List<RequestResponse>>> forMembership(@PathVariable UUID membershipId) {
        access.membership(membershipId, ResourceAccess.Level.READ);
        return ResponseEntity.ok(ApiResponse.success("Requests fetched", service.forMembership(membershipId)));
    }

    @GetMapping("/api/v1/properties/{propertyId}/requests")
    public ResponseEntity<ApiResponse<List<RequestResponse>>> forProperty(
            @PathVariable UUID propertyId, @RequestParam(required = false) Status status) {
        access.property(propertyId, ResourceAccess.Level.READ);
        return ResponseEntity.ok(ApiResponse.success("Requests fetched", service.forProperty(propertyId, status)));
    }

    @GetMapping("/api/v1/me/requests")
    public ResponseEntity<ApiResponse<List<RequestResponse>>> mine() {
        return ResponseEntity.ok(ApiResponse.success("Requests fetched", service.mine()));
    }

    @PostMapping("/api/v1/requests/{id}/approve")
    public ResponseEntity<ApiResponse<RequestResponse>> approve(@PathVariable UUID id,
                                                                @Valid @RequestBody(required = false) DecideRequestRequest req) {
        access.tenantRequest(id, false);
        return ResponseEntity.ok(ApiResponse.success("Request approved",
                service.approve(id, req == null ? new DecideRequestRequest() : req)));
    }

    @PostMapping("/api/v1/requests/{id}/reject")
    public ResponseEntity<ApiResponse<RequestResponse>> reject(@PathVariable UUID id,
                                                               @Valid @RequestBody DecideRequestRequest req) {
        access.tenantRequest(id, false);
        return ResponseEntity.ok(ApiResponse.success("Request rejected", service.reject(id, req)));
    }

    @PostMapping("/api/v1/requests/{id}/complete")
    public ResponseEntity<ApiResponse<RequestResponse>> complete(@PathVariable UUID id,
                                                                 @Valid @RequestBody(required = false) DecideRequestRequest req) {
        access.tenantRequest(id, false);
        return ResponseEntity.ok(ApiResponse.success("Request completed", service.complete(id, req)));
    }

    @PostMapping("/api/v1/requests/{id}/cancel")
    public ResponseEntity<ApiResponse<RequestResponse>> cancel(@PathVariable UUID id) {
        access.tenantRequest(id, true);
        return ResponseEntity.ok(ApiResponse.success("Request withdrawn", service.cancel(id)));
    }
}
