package com.renterp.domain.auth.entity;

import com.renterp.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private UserRole role = UserRole.LANDLORD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private KycStatus kycStatus = KycStatus.PENDING;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(length = 512)
    private String fcmToken;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String preferredLanguage = "en";

    // ── Credentials (V13) ─────────────────────────────────────────────────────
    // Unique case-insensitively (uq_users_email_lower). Null for accounts
    // created before email login existed.
    @Column(length = 254)
    private String email;

    // BCrypt hash. Never serialised: UserResponse does not expose it.
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "phone_verified_at")
    private Instant phoneVerifiedAt;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    // Consecutive wrong passwords; reset on success. Five in a row locks the
    // password login until loginLockedUntil (phone OTP still works).
    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private int failedLoginAttempts = 0;

    @Column(name = "login_locked_until")
    private Instant loginLockedUntil;

    // createdAt / updatedAt are inherited from BaseAuditEntity and set by JPA auditing

    public enum UserRole {
        LANDLORD, TENANT, ADMIN
    }

    public enum KycStatus {
        PENDING, SUBMITTED, VERIFIED, REJECTED
    }
}
