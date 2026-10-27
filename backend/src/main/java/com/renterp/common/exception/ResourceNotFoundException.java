package com.renterp.common.exception;

/**
 * 404. The response carries a specific code (e.g. METER_NOT_FOUND) and a plain
 * message ("Meter not found."); the internal field and value are logged only
 * (M18).
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String code;
    private final String detail;

    public ResourceNotFoundException(String resource, String field, Object value) {
        super(ErrorNames.words(resource) + " not found.");
        this.code = ErrorNames.code(resource) + "_NOT_FOUND";
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
