package com.renterp.domain.request.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Approve / reject / complete. A reject needs {@code note}. Approving a
 * ROOM_CHANGE with {@code fromRoomId} + {@code toRoomId} + {@code effectiveDateBs}
 * moves the tenant at once (the request is then COMPLETED).
 */
@Getter
@Setter
public class DecideRequestRequest {

    @Size(max = 1000)
    private String note;

    private UUID fromRoomId;

    private UUID toRoomId;

    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$", message = "effectiveDateBs must be YYYY-MM-DD (BS)")
    private String effectiveDateBs;
}
