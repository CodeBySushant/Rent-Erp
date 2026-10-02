package com.renterp.domain.payment.dto;

import com.renterp.domain.payment.entity.Payment.Method;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Owner recording money received (POST /tenant-bills/{id}/payments), or a
 * tenant sending proof (POST /tenant-bills/{id}/payment-proofs, which also
 * needs {@code proofFileId}: an upload with purpose PAYMENT_PROOF).
 */
@Getter
@Setter
public class RecordPaymentRequest {

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be more than zero")
    @Digits(integer = 8, fraction = 2, message = "amount has too many digits")
    private BigDecimal amount;

    @NotNull(message = "method is required (CASH, BANK, WALLET or OTHER)")
    private Method method;

    @NotBlank(message = "paidAtBs is required")
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$", message = "paidAtBs must be YYYY-MM-DD (BS)")
    private String paidAtBs;

    @Size(max = 100)
    private String reference;

    @Size(max = 500)
    private String note;

    private UUID proofFileId;
}
