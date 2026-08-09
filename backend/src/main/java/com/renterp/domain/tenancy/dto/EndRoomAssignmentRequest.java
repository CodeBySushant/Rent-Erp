package com.renterp.domain.tenancy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EndRoomAssignmentRequest {

    @NotBlank(message = "effectiveToBs is required")
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$",
             message = "effectiveToBs must be YYYY-MM-DD BS format")
    @Size(max = 20)
    private String effectiveToBs;
}
