package com.renterp.domain.property.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** PUT /properties/{id}/payment-details — every field optional; the whole set is replaced. */
@Getter
@Setter
public class PaymentDetailsRequest {

    /** An upload with purpose PAYMENT_QR for this property. */
    private UUID qrFileId;

    @Size(max = 50)
    private String walletName;

    @Size(max = 100)
    private String walletId;

    @Size(max = 100)
    private String bankName;

    @Size(max = 150)
    private String accountName;

    @Size(max = 50)
    private String accountNumber;

    @Size(max = 100)
    private String branch;

    @Size(max = 500)
    private String notes;
}
