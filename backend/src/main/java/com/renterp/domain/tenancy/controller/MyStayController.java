package com.renterp.domain.tenancy.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.domain.tenancy.dto.MyStayResponse;
import com.renterp.domain.tenancy.service.MyStayService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in tenant's own home data. Always about the caller; there is no id to pass. */
@RestController
public class MyStayController {

    private final MyStayService service;

    public MyStayController(MyStayService service) {
        this.service = service;
    }

    // ── GET /api/v1/me/stay ──────────────────────────────────────────────────────
    @GetMapping("/api/v1/me/stay")
    public ResponseEntity<ApiResponse<MyStayResponse>> myStay() {
        return ResponseEntity.ok(ApiResponse.success("Stay fetched", service.myStay()));
    }
}
