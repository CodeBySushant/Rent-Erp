package com.renterp.common.exception;

/**
 * A request is well-formed and every referenced resource exists, but the operation
 * itself violates a business rule (e.g. modifying the auto-created OWNER access grant).
 * Maps to 400, distinct from bean-validation failures on the request body.
 */
public class InvalidOperationException extends RuntimeException {

    public InvalidOperationException(String message) {
        super(message);
    }
}
