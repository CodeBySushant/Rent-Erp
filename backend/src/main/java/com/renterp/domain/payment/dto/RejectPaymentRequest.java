package com.renterp.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RejectPaymentRequest {

    @NotBlank(message = "reason is required")
    @Size(max = 500)
    private String reason;
}
