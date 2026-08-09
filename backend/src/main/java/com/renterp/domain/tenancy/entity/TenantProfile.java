package com.renterp.domain.tenancy.entity;

import com.renterp.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "tenant_profiles")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TenantProfile extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    // NULL for unlinked tenants (landlord entered them, no app account).
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "whatsapp_number", length = 20)
    private String whatsappNumber;

    @Column(name = "preferred_language", nullable = false, length = 5)
    @Builder.Default
    private String preferredLanguage = "en";

    @Enumerated(EnumType.STRING)
    @Column(name = "tenant_category", nullable = false, length = 20)
    @Builder.Default
    private TenantCategory tenantCategory = TenantCategory.INDIVIDUAL;

    @Column(name = "num_occupants", nullable = false)
    @Builder.Default
    private short numOccupants = 1;

    @Column(name = "emergency_contact_name", length = 255)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    public enum TenantCategory { INDIVIDUAL, FAMILY, BUSINESS }
}
