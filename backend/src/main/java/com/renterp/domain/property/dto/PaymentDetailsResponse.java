package com.renterp.domain.property.dto;

import com.renterp.domain.property.entity.PropertyPaymentDetails;

import java.time.Instant;
import java.util.UUID;

public record PaymentDetailsResponse(UUID propertyId, UUID qrFileId, String qrUrl, String walletName, String walletId,
                                     String bankName, String accountName, String accountNumber, String branch,
                                     String notes, Instant updatedAt) {

    public static PaymentDetailsResponse from(PropertyPaymentDetails d) {
        return new PaymentDetailsResponse(d.getPropertyId(), d.getQrFileId(),
                d.getQrFileId() == null ? null : "/api/v1/files/" + d.getQrFileId() + "/content",
                d.getWalletName(), d.getWalletId(), d.getBankName(), d.getAccountName(), d.getAccountNumber(),
                d.getBranch(), d.getNotes(), d.getUpdatedAt());
    }
}
