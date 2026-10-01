package com.renterp.domain.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "otp_attempts")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "is_used", nullable = false)
    @Builder.Default
    private boolean used = false;

    // ── V13 ───────────────────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Purpose purpose = Purpose.LOGIN;

    // Set when a SIGNUP code is checked successfully.
    private Instant verifiedAt;

    // SHA-256 (hex) of the single-use token /auth/otp/verify hands back;
    // /auth/register spends it (tokenUsedAt).
    @Column(length = 64)
    private String verificationTokenHash;

    private Instant tokenUsedAt;

    // The account a CHANGE_PHONE code was requested by.
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public enum Purpose {
        SIGNUP, LOGIN, CHANGE_PHONE
    }
}
