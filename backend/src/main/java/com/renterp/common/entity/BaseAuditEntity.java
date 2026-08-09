package com.renterp.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Base class for mutable entities that need both created and last-modified timestamps.
 *
 * created_at — set once on insert, never updated (updatable = false)
 * updated_at — set on insert and refreshed on every update
 *
 * Both are populated by Spring Data JPA auditing (see JpaAuditingConfig).
 * Append-only entities (e.g. OtpAttempt, UserSession) use @CreatedDate alone instead
 * of extending this class, since their tables have no updated_at column.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class BaseAuditEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
