package com.renterp.domain.tenancy.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KycDecisionRequest {
    // Required on REJECT and FLAG; optional (may be null) on APPROVE.
    @NotBlank(message = "reason is required")
    private String reason;
}
