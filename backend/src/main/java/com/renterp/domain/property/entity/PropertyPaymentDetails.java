package com.renterp.domain.property.entity;

import com.renterp.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/** How tenants pay the owner of one property (V21). */
@Entity
@Table(name = "property_payment_details")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyPaymentDetails extends BaseAuditEntity {

    @Id
    @Column(name = "property_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID propertyId;

    @Column(name = "qr_file_id", columnDefinition = "uuid")
    private UUID qrFileId;

    @Column(name = "wallet_name", length = 50)
    private String walletName;

    @Column(name = "wallet_id", length = 100)
    private String walletId;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "account_name", length = 150)
    private String accountName;

    @Column(name = "account_number", length = 50)
    private String accountNumber;

    @Column(length = 100)
    private String branch;

    @Column(length = 500)
    private String notes;

    @Column(name = "updated_by", columnDefinition = "uuid")
    private UUID updatedBy;
}
