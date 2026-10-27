package com.renterp.common.exception;

/**
 * A request is well-formed and every referenced resource exists, but the operation
 * itself violates a business rule (e.g. modifying the auto-created OWNER access grant).
 * Maps to 400, distinct from bean-validation failures on the request body.
 *
 * The message text is kept as is: the app matches a few of these sentences to
 * offer a next step (rollover, zero amount, oldest-first). New code may pass a
 * specific error code (M18); otherwise the code is INVALID_OPERATION.
 */
public class InvalidOperationException extends RuntimeException {

    private final String code;

    public InvalidOperationException(String message) {
        this("INVALID_OPERATION", message);
    }

    public InvalidOperationException(String code, String message) {
        super(message);
        this.code = code == null ? "INVALID_OPERATION" : code;
    }

    public String getCode() {
        return code;
    }
}
