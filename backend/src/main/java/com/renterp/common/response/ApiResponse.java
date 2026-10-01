package com.renterp.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.Instant;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String message;

    /**
     * Machine-readable error code on failures (e.g. ROOM_ALREADY_OCCUPIED,
     * TOKEN_EXPIRED). Omitted on success. Clients branch on this, never on
     * the message text.
     */
    private final String code;
    private final T data;
    private final Instant timestamp;

    private ApiResponse(boolean success, String message, String code, T data) {
        this.success = success;
        this.message = message;
        this.code = code;
        this.data = data;
        this.timestamp = Instant.now();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, null, data);
    }

    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(true, message, null, null);
    }

    public static <T> ApiResponse<T> failure(String message) {
        return new ApiResponse<>(false, message, null, null);
    }

    public static <T> ApiResponse<T> failure(String message, String code) {
        return new ApiResponse<>(false, message, code, null);
    }
}
