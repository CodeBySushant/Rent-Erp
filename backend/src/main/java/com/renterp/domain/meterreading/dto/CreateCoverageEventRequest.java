package com.renterp.domain.meterreading.dto;

import com.renterp.domain.meterreading.entity.MeterCoverageEvent.CoverageEventType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class CreateCoverageEventRequest {

    @NotNull(message = "propertyId is required")
    private UUID propertyId;

    @NotNull(message = "eventType is required")
    private CoverageEventType eventType;

    @NotBlank(message = "eventDateBs is required")
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$",
             message = "eventDateBs must be YYYY-MM-DD BS format")
    @Size(max = 20)
    private String eventDateBs;

    private String notes;

    // One entry per meter affected by this event. Every meter that either gains or
    // loses coverage MUST include an anchorReading (§14.1 M9). A meter that only
    // sheds coverage may omit anchorReading if the landlord chooses (rare — usually
    // needed for the surviving meter's chain).
    @NotNull(message = "affectedMeters is required")
    @Size(min = 1, message = "at least one affected meter is required")
    @Valid
    private List<AffectedMeter> affectedMeters;

    @Getter
    @Setter
    public static class AffectedMeter {

        @NotNull
        private UUID meterId;

        // Rooms added to this meter's coverage — new meter_room_coverage rows opened.
        private List<UUID> roomsAdded = List.of();

        // Rooms removed — their existing active coverage rows are ended on eventDateBs.
        private List<UUID> roomsRemoved = List.of();

        // Reading captured off this meter as an anchor for the changed chain (§14.1 M9).
        // Optional but recommended; omit only when the meter is being fully removed.
        @Valid
        private AnchorReading anchorReading;
    }

    @Getter
    @Setter
    public static class AnchorReading {

        @NotNull @DecimalMin(value = "0.00")
        private BigDecimal readingValue;

        @Size(max = 500)
        private String photoUrl;

        private String notes;
    }
}
