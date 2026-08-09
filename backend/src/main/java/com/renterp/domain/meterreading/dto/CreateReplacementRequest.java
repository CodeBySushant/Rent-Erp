package com.renterp.domain.meterreading.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateReplacementRequest {

    // The URL path carries the OLD (being replaced) meter's id — the "surviving"
    // logical meter identity moves to the new one via meters.replaced_by_meter_id.

    @NotBlank(message = "replacementDateBs is required")
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$",
             message = "replacementDateBs must be YYYY-MM-DD BS format")
    @Size(max = 20)
    private String replacementDateBs;

    private String reason;

    // Final reading captured off the old meter (§14.1 M4). If the meter died and
    // no reading can be taken, set closeReading.estimated=true with an
    // estimationBasis — the row is flagged (§14.1 M5).
    @NotNull(message = "closeReading is required")
    @Valid
    private ReadingSpec closeReading;

    // Opening reading on the new meter — may be any non-zero value; deltas within a
    // single chain are what matter (§14.1 M6).
    @NotNull(message = "openReading is required")
    @Valid
    private ReadingSpec openReading;

    // New meter's identifying info. All other characteristics (purpose, type, scope,
    // reading responsibility, split override, max value) are carried forward from
    // the old meter, matching spec §7.8 "coverage/assignment/split carry forward".
    @NotBlank(message = "newMeterLabel is required")
    @Size(max = 255)
    private String newMeterLabel;

    @Size(max = 100)
    private String newMeterSerialNumber;

    @Getter
    @Setter
    public static class ReadingSpec {

        @NotNull @DecimalMin(value = "0.00")
        private BigDecimal readingValue;

        // Optional. Defaults to false. For the CLOSE side, mark true when reading is
        // estimated (M5); estimationBasis must accompany it.
        private boolean estimated = false;

        private com.renterp.domain.meterreading.entity.MeterReading.EstimationBasis estimationBasis;

        @Size(max = 500)
        private String photoUrl;

        private String notes;
    }
}
