package com.renterp.domain.moveout.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.domain.auth.security.ResourceAccess;
import com.renterp.domain.moveout.dto.MoveOutNoticeRequest;
import com.renterp.domain.moveout.dto.MoveOutResponse;
import com.renterp.domain.moveout.dto.SettleMoveOutRequest;
import com.renterp.domain.moveout.entity.MoveOut.Status;
import com.renterp.domain.moveout.service.MoveOutService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Move-out notice (tenant or owner) and settlement (owner). */
@RestController
public class MoveOutController {

    private final MoveOutService service;
    private final ResourceAccess access;

    public MoveOutController(MoveOutService service, ResourceAccess access) {
        this.service = service;
        this.access = access;
    }

    @PostMapping("/api/v1/memberships/{membershipId}/move-out")
    public ResponseEntity<ApiResponse<MoveOutResponse>> notice(@PathVariable UUID membershipId,
                                                               @Valid @RequestBody MoveOutNoticeRequest req) {
        access.membershipSelfOrManager(membershipId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Move-out notice given", service.giveNotice(membershipId, req)));
    }

    @GetMapping("/api/v1/memberships/{membershipId}/move-out")
    public ResponseEntity<ApiResponse<MoveOutResponse>> latest(@PathVariable UUID membershipId) {
        access.membership(membershipId, ResourceAccess.Level.READ);
        return ResponseEntity.ok(ApiResponse.success("Move-out fetched", service.latest(membershipId).orElse(null)));
    }

    @PostMapping("/api/v1/move-outs/{id}/cancel")
    public ResponseEntity<ApiResponse<MoveOutResponse>> cancel(@PathVariable UUID id) {
        access.moveOut(id, true);
        return ResponseEntity.ok(ApiResponse.success("Move-out cancelled", service.cancel(id)));
    }

    @PostMapping("/api/v1/move-outs/{id}/settle")
    public ResponseEntity<ApiResponse<MoveOutResponse>> settle(@PathVariable UUID id,
                                                               @Valid @RequestBody SettleMoveOutRequest req) {
        access.moveOut(id, false);
        return ResponseEntity.ok(ApiResponse.success("Move-out settled", service.settle(id, req)));
    }

    @GetMapping("/api/v1/properties/{propertyId}/move-outs")
    public ResponseEntity<ApiResponse<List<MoveOutResponse>>> forProperty(
            @PathVariable UUID propertyId, @RequestParam(required = false) Status status) {
        access.property(propertyId, ResourceAccess.Level.READ);
        return ResponseEntity.ok(ApiResponse.success("Move-outs fetched", service.forProperty(propertyId, status)));
    }
}
