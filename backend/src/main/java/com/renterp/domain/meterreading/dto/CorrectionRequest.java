package com.renterp.domain.meterreading.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CorrectionRequest {

    // Corrections adopt the corrected reading's date (§14.1 M16 pattern — a correction
    // is *for* the original's physical read date, not a fresh submission). Only the value,
    // photo, and notes may differ.

    @NotNull(message = "correctedValue is required")
    @DecimalMin(value = "0.00", message = "correctedValue cannot be negative")
    private BigDecimal correctedValue;

    // Optional; if omitted, submissionDateBs on the correction row will be same as the
    // original reading's submissionDateBs.
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$",
             message = "submissionDateBs must be YYYY-MM-DD BS format")
    @Size(max = 20)
    private String submissionDateBs;

    @Size(max = 500)
    private String photoUrl;

    @NotBlank(message = "notes are required on a correction (audit trail)")
    private String notes;
}
