package com.renterp.common.exception;

/**
 * 409. Specific code (e.g. ROOM_ALREADY_EXISTS) and a plain message; the
 * internal field and value are logged only (M18).
 */
public class DuplicateResourceException extends RuntimeException {

    private final String code;
    private final String detail;

    public DuplicateResourceException(String resource, String field, Object value) {
        super(ErrorNames.words(resource) + " already exists.");
        this.code = ErrorNames.code(resource) + "_ALREADY_EXISTS";
        this.detail = resource + " " + field + "=" + value;
    }

    public String getCode() {
        return code;
    }

    /** For logs only. */
    public String getDetail() {
        return detail;
    }
}
