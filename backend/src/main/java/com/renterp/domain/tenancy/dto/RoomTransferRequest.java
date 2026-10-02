package com.renterp.domain.tenancy.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** Move a tenant from one of their rooms to a vacant room, from a date. */
@Getter
@Setter
public class RoomTransferRequest {

    @NotNull(message = "fromRoomId is required")
    private UUID fromRoomId;

    @NotNull(message = "toRoomId is required")
    private UUID toRoomId;

    @NotBlank(message = "effectiveDateBs is required")
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$", message = "effectiveDateBs must be YYYY-MM-DD (BS)")
    private String effectiveDateBs;

    /** Rent in the new room; defaults to the old room's rent. */
    @PositiveOrZero @Digits(integer = 8, fraction = 2)
    private BigDecimal monthlyRent;
}
