package com.renterp.domain.tenantfinance.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class CreateRentIncrementRequest {

    @NotNull(message = "roomAssignmentId is required")
    private UUID roomAssignmentId;

    @NotNull(message = "newAmount is required")
    @DecimalMin(value = "0.00", message = "newAmount cannot be negative")
    private BigDecimal newAmount;

    @NotBlank
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$",
             message = "effectiveBs must be YYYY-MM-DD BS format")
    @Size(max = 20)
    private String effectiveBs;

    private String reason;

    // Landlord's choice whether to notify (spec §10.5). Actual send happens once Phase 8 lands.
    private boolean notifiedTenant = false;

    // previousAmount is deliberately NOT accepted from the client — the service captures the
    // room_assignment's current monthlyRent at apply time so history is always accurate.
}
