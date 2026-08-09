package com.renterp.domain.billing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CancelBillingRunRequest {

    @NotBlank
    private String reason;

    // Landlord user id; becomes an FK when auth lands.
    private UUID cancelledBy;
}
