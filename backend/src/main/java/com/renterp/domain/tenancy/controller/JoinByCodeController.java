package com.renterp.domain.tenancy.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.domain.tenancy.dto.JoinByCodeRequest;
import com.renterp.domain.tenancy.dto.JoinLookupResponse;
import com.renterp.domain.tenancy.dto.JoinRequestResponse;
import com.renterp.domain.tenancy.service.JoinByCodeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Tenant side of "join a property": look the code up, then ask to join. */
@RestController
@RequestMapping("/api/v1/join")
public class JoinByCodeController {

    private final JoinByCodeService service;

    public JoinByCodeController(JoinByCodeService service) {
        this.service = service;
    }

    // ── GET /api/v1/join/{code} — "Property found" ─────────────────────────────
    @GetMapping("/{code}")
    public ResponseEntity<ApiResponse<JoinLookupResponse>> lookup(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success("Property found", service.lookup(code)));
    }

    // ── POST /api/v1/join/{code} — send the request to the owner ──────────────
    @PostMapping("/{code}")
    public ResponseEntity<ApiResponse<JoinRequestResponse>> join(@PathVariable String code,
                                                                 @Valid @RequestBody(required = false) JoinByCodeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Join request sent",
                        service.join(code, req == null ? null : req.getMessage())));
    }
}
