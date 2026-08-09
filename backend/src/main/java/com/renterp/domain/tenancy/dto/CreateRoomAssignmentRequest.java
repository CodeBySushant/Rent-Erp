package com.renterp.domain.tenancy.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class CreateRoomAssignmentRequest {

    @NotNull(message = "roomId is required")
    private UUID roomId;

    @NotBlank(message = "effectiveFromBs is required")
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$",
             message = "effectiveFromBs must be YYYY-MM-DD BS format")
    @Size(max = 20)
    private String effectiveFromBs;

    // Required as of V11 (rent storage lives on the assignment). Nullable in the
    // DB column so the tenancy-pass fixture rows remain valid; the API refuses
    // creates without it.
    @NotNull(message = "monthlyRent is required")
    @DecimalMin(value = "0.00", message = "monthlyRent cannot be negative")
    private BigDecimal monthlyRent;
}
