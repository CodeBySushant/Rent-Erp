package com.renterp.domain.identity.entity;

import com.renterp.common.entity.BaseAuditEntity;
import com.renterp.domain.tenancy.entity.TenantKyc.IdType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/** An owner's ID document and selfie (V24). Stored only; never reviewed. */
@Entity
@Table(name = "owner_identity")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerIdentity extends BaseAuditEntity {
    @Id
    @Column(name = "user_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(name = "id_type", nullable = false, length = 30)
    private IdType idType;
    @Column(name = "id_number", nullable = false, length = 100)
    private String idNumber;
    @Column(name = "id_front_file_id", columnDefinition = "uuid", nullable = false)
    private UUID idFrontFileId;
    @Column(name = "id_back_file_id", columnDefinition = "uuid")
    private UUID idBackFileId;
    @Column(name = "selfie_file_id", columnDefinition = "uuid", nullable = false)
    private UUID selfieFileId;
    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;
}
