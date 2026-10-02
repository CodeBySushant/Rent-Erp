package com.renterp.domain.property.service;

import com.renterp.common.exception.ApiException;
import com.renterp.domain.auth.security.AccessGuard;
import com.renterp.domain.file.entity.StoredFile.FilePurpose;
import com.renterp.domain.file.repository.StoredFileRepository;
import com.renterp.domain.property.dto.PaymentDetailsRequest;
import com.renterp.domain.property.dto.PaymentDetailsResponse;
import com.renterp.domain.property.entity.PropertyPaymentDetails;
import com.renterp.domain.property.repository.PropertyPaymentDetailsRepository;
import com.renterp.domain.property.repository.PropertyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * The owner's payment details per property (QR, wallet, bank). The QR must be
 * an upload with purpose PAYMENT_QR tied to the same property, so the file
 * rules already let the property's active tenants open it. Authorization is
 * done by the controller.
 */
@Service
public class PaymentDetailsService {

    private final PropertyPaymentDetailsRepository details;
    private final PropertyRepository properties;
    private final StoredFileRepository files;
    private final AccessGuard guard;

    public PaymentDetailsService(PropertyPaymentDetailsRepository details, PropertyRepository properties,
                                 StoredFileRepository files, AccessGuard guard) {
        this.details = details;
        this.properties = properties;
        this.files = files;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public Optional<PaymentDetailsResponse> get(UUID propertyId) {
        return details.findById(propertyId).map(PaymentDetailsResponse::from);
    }

    @Transactional
    public PaymentDetailsResponse save(UUID propertyId, PaymentDetailsRequest req) {
        if (!properties.existsById(propertyId)) {
            throw ApiException.notFound("PROPERTY_NOT_FOUND", "Property not found.");
        }
        if (req.getQrFileId() != null) {
            files.findByIdAndDeletedAtIsNull(req.getQrFileId())
                    .filter(f -> f.getPurpose() == FilePurpose.PAYMENT_QR && propertyId.equals(f.getPropertyId()))
                    .orElseThrow(() -> ApiException.badRequest("QR_INVALID",
                            "Upload the QR as a payment QR for this property first."));
        }
        boolean empty = req.getQrFileId() == null && blank(req.getWalletId()) && blank(req.getAccountNumber());
        if (empty) {
            throw ApiException.badRequest("DETAILS_REQUIRED",
                    "Add a QR, a wallet ID or a bank account so tenants know how to pay.");
        }
        PropertyPaymentDetails d = details.findById(propertyId)
                .orElseGet(() -> PropertyPaymentDetails.builder().propertyId(propertyId).build());
        d.setQrFileId(req.getQrFileId());
        d.setWalletName(trim(req.getWalletName()));
        d.setWalletId(trim(req.getWalletId()));
        d.setBankName(trim(req.getBankName()));
        d.setAccountName(trim(req.getAccountName()));
        d.setAccountNumber(trim(req.getAccountNumber()));
        d.setBranch(trim(req.getBranch()));
        d.setNotes(trim(req.getNotes()));
        d.setUpdatedBy(guard.requireUser().userId());
        return PaymentDetailsResponse.from(details.saveAndFlush(d));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return blank(s) ? null : s.trim();
    }
}
