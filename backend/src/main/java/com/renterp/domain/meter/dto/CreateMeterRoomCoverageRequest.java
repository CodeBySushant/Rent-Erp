package com.renterp.domain.meter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreateMeterRoomCoverageRequest {

    @NotNull(message = "Room id is required")
    private UUID roomId;

    // BS date VARCHAR (YYYY-MM-DD, e.g. "2082-04-01"). Format enforced here; day/month
    // ranges will be validated more rigorously once the BS-calendar library is wired in.
    @NotBlank(message = "effectiveFromBs is required")
    @Size(max = 20)
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$",
             message = "effectiveFromBs must be a BS date in YYYY-MM-DD format")
    private String effectiveFromBs;
}
