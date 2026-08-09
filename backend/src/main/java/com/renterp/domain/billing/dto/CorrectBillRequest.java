package com.renterp.domain.billing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request to correct a confirmed (ISSUED) tenant bill (B8). Supply either the fully-corrected
 * {@link #correctedTotalDue} or a signed {@link #deltaAmount}; the engine picks the path from
 * the bill's payment status — unpaid → cancel + regenerate a replacement bill; paid/partial →
 * carry the delta onto the next bill as an adjustment.
 */
@Getter
@Setter
public class CorrectBillRequest {

    // The intended final total for the bill. Mutually exclusive with deltaAmount.
    private BigDecimal correctedTotalDue;

    // Signed change to apply (+ tenant owes more, − tenant owed less). Used when
    // correctedTotalDue is null.
    private BigDecimal deltaAmount;

    @NotBlank
    private String reason;

    private UUID correctedBy;
}
