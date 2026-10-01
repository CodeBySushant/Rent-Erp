package com.renterp.common.exception;

import com.renterp.common.response.ApiResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * Every error leaves the API as {@code {success:false, message, code, timestamp}}.
 * Stack traces are logged, never returned.
 *
 * Status codes: 400 invalid request or business rule, 401 not authenticated,
 * 403 not allowed, 404 not found, 405 wrong method, 409 conflict, 410 expired
 * or already used, 429 rate limited, 503 required service not configured,
 * 500 anything unexpected.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LogManager.getLogger(GlobalExceptionHandler.class);

    // ── Exceptions that carry their own status + code ─────────────────────────
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApi(ApiException ex) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("API error {} {}: {}", ex.getStatus().value(), ex.getCode(), ex.getMessage());
        } else {
            log.warn("API error {} {}: {}", ex.getStatus().value(), ex.getCode(), ex.getMessage());
        }
        return ResponseEntity
                .status(ex.getStatus())
                .body(ApiResponse.failure(ex.getMessage(), ex.getCode()));
    }

    // ── 404 Not Found ──────────────────────────────────────────────────────────
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(ex.getMessage(), "NOT_FOUND"));
    }

    // Unknown URL (no controller, no static resource).
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure("No endpoint at this path", "NOT_FOUND"));
    }

    // ── 409 Conflict ───────────────────────────────────────────────────────────
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateResourceException ex) {
        log.warn("Duplicate resource: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure(ex.getMessage(), "DUPLICATE"));
    }

    // A unique / foreign-key / check constraint fired that a service did not
    // pre-check (or lost a race). The constraint detail is logged, not returned.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure(
                        "This change conflicts with existing data. Refresh and try again.",
                        "DATA_CONFLICT"));
    }

    // ── 400 Business rule violation (well-formed request, invalid operation) ───
    @ExceptionHandler(InvalidOperationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidOperation(InvalidOperationException ex) {
        log.warn("Invalid operation: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(ex.getMessage(), "INVALID_OPERATION"));
    }

    // ── 400 Validation failure (@Valid on request body) ────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        log.warn("Validation failed: {}", errors);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(errors, "VALIDATION_FAILED"));
    }

    // ── 400 Malformed input ────────────────────────────────────────────────────
    // Unparseable JSON, wrong JSON types, unknown enum values. Used to be a 500.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(
                        "The request body is malformed or has a value of the wrong type.",
                        "MALFORMED_REQUEST"));
    }

    // A path or query value of the wrong type, e.g. /users/abc for a UUID id.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Bad parameter {}: {}", ex.getName(), ex.getValue());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(
                        "Invalid value for '" + ex.getName() + "'.", "INVALID_PARAMETER"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(
                        "Missing required parameter '" + ex.getParameterName() + "'.",
                        "MISSING_PARAMETER"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.failure(
                        "Method " + ex.getMethod() + " is not supported here.", "METHOD_NOT_ALLOWED"));
    }

    // ── Uploads ────────────────────────────────────────────────────────────────
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.failure("This file is too large to upload.", "FILE_TOO_LARGE"));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(
                        "Missing required part '" + ex.getRequestPartName() + "'.", "MISSING_PARAMETER"));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("The upload could not be read. Send it as multipart/form-data.",
                        "MALFORMED_REQUEST"));
    }

    // ── 403 from Spring Security method checks ────────────────────────────────
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failure("You do not have access to this resource.", "FORBIDDEN"));
    }

    // ── 500 Catch-all ──────────────────────────────────────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(
                        "An unexpected error occurred. Please try again.", "INTERNAL_ERROR"));
    }
}
