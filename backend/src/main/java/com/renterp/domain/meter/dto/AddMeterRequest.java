package com.renterp.domain.meter.dto;

import com.renterp.common.validation.BsDate;
import com.renterp.domain.meter.entity.Meter.MeterType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * POST /api/v1/properties/{propertyId}/meters — an electricity meter added
 * after setup, with its opening reading and (for a sub meter) its rooms, all
 * in one transaction.
 */
@Getter
@Setter
public class AddMeterRequest {

    @NotBlank(message = "Enter the meter name")
    @Size(max = 255, message = "The meter name can be at most 255 characters")
    private String label;

    @Size(max = 100, message = "The serial number can be at most 100 characters")
    private String serialNumber;

    /** MAIN or TENANT_SUPPLY (a sub meter). */
    @NotNull(message = "Choose main meter or sub meter")
    private MeterType meterType;

    /** The meter's reading today, the start of its first bill. */
    @NotNull(message = "Enter the opening reading")
    @DecimalMin(value = "0.00", message = "The opening reading cannot be negative")
    @Digits(integer = 8, fraction = 2, message = "The opening reading has too many digits")
    private BigDecimal openingReading;

    /** Defaults to today. */
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$", message = "readingDateBs must be YYYY-MM-DD (BS)")
    @BsDate
    private String readingDateBs;

    /** Rooms a sub meter covers (none for a main meter). */
    @Size(max = 200, message = "Too many rooms")
    private List<@NotNull UUID> roomIds;
}
