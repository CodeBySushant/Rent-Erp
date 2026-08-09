package com.renterp.domain.auth.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.common.response.PagedResponse;
import com.renterp.domain.auth.dto.CreateUserRequest;
import com.renterp.domain.auth.dto.UpdateUserRequest;
import com.renterp.domain.auth.dto.UserResponse;
import com.renterp.domain.auth.service.UserService;
import jakarta.validation.Valid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final Logger log = LogManager.getLogger(UserController.class);

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // ── POST /api/v1/users ─────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request) {

        log.debug("POST /api/v1/users — phone: {}", request.getPhone());
        UserResponse response = userService.createUser(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created successfully", response));
    }

    // ── GET /api/v1/users/{id} ─────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable UUID id) {
        log.debug("GET /api/v1/users/{}", id);
        UserResponse response = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success("User fetched successfully", response));
    }

    // ── GET /api/v1/users ──────────────────────────────────────────────────────
    // Pagination params (all optional — defaults applied below):
    //   ?page=0       → which page to fetch (0-based, so page=0 is the first page)
    //   ?size=20      → how many records per page
    //   ?sort=createdAt,desc  → field to sort by and direction
    //
    // Example: GET /api/v1/users?page=1&size=10&sort=createdAt,desc
    //   → fetch the 2nd page, 10 users each, newest first
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<UserResponse>>> getAllUsers(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        log.debug("GET /api/v1/users — page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        Page<UserResponse> page = userService.getAllUsers(pageable);
        return ResponseEntity.ok(ApiResponse.success("Users fetched successfully", PagedResponse.from(page)));
    }

    // ── PUT /api/v1/users/{id} ─────────────────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {

        log.debug("PUT /api/v1/users/{}", id);
        UserResponse response = userService.updateUser(id, request);
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", response));
    }

    // ── DELETE /api/v1/users/{id} ──────────────────────────────────────────────
    // Soft delete only — sets is_active = false. Row is never removed from DB.
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable UUID id) {
        log.debug("DELETE /api/v1/users/{}", id);
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("User deactivated successfully"));
    }
}
