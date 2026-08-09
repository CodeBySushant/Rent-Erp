package com.renterp.domain.tenantfinance.entity;

import com.renterp.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "tenant_opening_balances")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TenantOpeningBalance extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "membership_id", nullable = false, columnDefinition = "uuid")
    private UUID membershipId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Direction direction;

    @Column(name = "as_of_bs", nullable = false, length = 20)
    private String asOfBs;

    @Column(columnDefinition = "TEXT")
    private String notes;

    public enum Direction { OWED_BY_TENANT, CREDIT_TO_TENANT }
}
