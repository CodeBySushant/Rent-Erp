package com.renterp.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base for exceptions that carry their own HTTP status and machine-readable
 * error code. {@link GlobalExceptionHandler} turns any of them into
 * {@code {success:false, message, code}} with that status.
 *
 * The pre-existing exceptions (ResourceNotFound, DuplicateResource,
 * InvalidOperation) are kept as they are; their codes are assigned in the
 * handler so existing callers do not change.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    /** 401 - no valid credentials. */
    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }

    /** 403 - authenticated, but not allowed to touch this resource. */
    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    /** 409 - the request conflicts with current state. */
    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    /** 400 - a business rule rejects this request. */
    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    /** 404 - with a specific code. */
    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    /** 410 - something that existed has expired or was already used (OTP, verification token). */
    public static ApiException gone(String code, String message) {
        return new ApiException(HttpStatus.GONE, code, message);
    }

    /** 429 - rate limited. */
    public static ApiException tooManyRequests(String code, String message) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, code, message);
    }

    /** 503 - a required external service (SMS, storage) is not configured. */
    public static ApiException unavailable(String code, String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }
}
