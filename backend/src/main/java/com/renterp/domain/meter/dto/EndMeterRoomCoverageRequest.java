package com.renterp.domain.meter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EndMeterRoomCoverageRequest {

    @NotBlank(message = "effectiveToBs is required")
    @Size(max = 20)
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$",
             message = "effectiveToBs must be a BS date in YYYY-MM-DD format")
    private String effectiveToBs;
}
