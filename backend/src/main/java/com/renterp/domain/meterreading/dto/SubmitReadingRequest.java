package com.renterp.domain.meterreading.dto;

import com.renterp.domain.meterreading.entity.MeterReading.ReadingType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class SubmitReadingRequest {

    // meterId comes from the URL path, not the body.

    @NotNull(message = "readingType is required")
    private ReadingType readingType;

    @NotNull(message = "readingValue is required")
    @DecimalMin(value = "0.00", message = "readingValue cannot be negative")
    private BigDecimal readingValue;

    @NotBlank(message = "readingDateBs is required")
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$",
             message = "readingDateBs must be YYYY-MM-DD BS format")
    @Size(max = 20)
    private String readingDateBs;

    // Optional; defaults to readingDateBs at the service layer if omitted.
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$",
             message = "submissionDateBs must be YYYY-MM-DD BS format")
    @Size(max = 20)
    private String submissionDateBs;

    // Spec §14.1 M1 — must be set true when readingValue is less than the previous
    // confirmed reading and the landlord is confirming the meter rolled over.
    private boolean rolloverConfirmed = false;

    @Size(max = 500)
    private String photoUrl;

    private String notes;
}
