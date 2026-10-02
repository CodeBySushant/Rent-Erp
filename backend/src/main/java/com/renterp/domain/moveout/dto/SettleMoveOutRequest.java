package com.renterp.domain.moveout.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Owner settles a move-out. {@code finalCharges} covers anything not billed
 * yet (e.g. the last days' electricity); {@code deductions} damages or
 * cleaning. Both reduce the refund.
 */
@Getter
@Setter
public class SettleMoveOutRequest {

    @NotBlank(message = "movedOutBs is required")
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$", message = "movedOutBs must be YYYY-MM-DD (BS)")
    private String movedOutBs;

    @PositiveOrZero @Digits(integer = 8, fraction = 2)
    private BigDecimal finalCharges;

    @Size(max = 500)
    private String finalChargesNote;

    @PositiveOrZero @Digits(integer = 8, fraction = 2)
    private BigDecimal deductions;

    @Size(max = 500)
    private String deductionsNote;
}
