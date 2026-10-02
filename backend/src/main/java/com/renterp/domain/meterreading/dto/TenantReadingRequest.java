package com.renterp.domain.meterreading.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** A tenant's monthly reading of their own meter (always reviewed by the owner). */
@Getter
@Setter
public class TenantReadingRequest {

    @NotNull(message = "readingValue is required")
    @PositiveOrZero(message = "readingValue cannot be negative")
    @Digits(integer = 10, fraction = 2, message = "readingValue has too many digits")
    private BigDecimal readingValue;

    /** Upload with purpose METER_PHOTO; optional but recommended. */
    private UUID photoFileId;

    @Size(max = 500)
    private String notes;
}
