package com.renterp.domain.tenancy.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.domain.auth.security.ResourceAccess;
import com.renterp.domain.tenancy.dto.RoomTransferRequest;
import com.renterp.domain.tenancy.dto.RoomTransferResponse;
import com.renterp.domain.tenancy.service.RoomTransferService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Owner moves a tenant to another (vacant) room. */
@RestController
public class RoomTransferController {

    private final RoomTransferService service;
    private final ResourceAccess access;

    public RoomTransferController(RoomTransferService service, ResourceAccess access) {
        this.service = service;
        this.access = access;
    }

    // ── POST /api/v1/memberships/{membershipId}/room-transfer ────────────────────
    @PostMapping("/api/v1/memberships/{membershipId}/room-transfer")
    public ResponseEntity<ApiResponse<RoomTransferResponse>> transfer(@PathVariable UUID membershipId,
                                                                      @Valid @RequestBody RoomTransferRequest req) {
        access.membership(membershipId, ResourceAccess.Level.WRITE);
        return ResponseEntity.ok(ApiResponse.success("Room transferred", service.transfer(membershipId, req)));
    }
}
