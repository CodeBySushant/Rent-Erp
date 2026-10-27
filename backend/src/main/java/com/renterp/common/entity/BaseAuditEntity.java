package com.renterp.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
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
 *
 * version — optimistic lock (V25, issue L4). Hibernate increments it on every
 *           update and refuses an update made from a stale copy, so two
 *           people editing the same row cannot silently overwrite each other;
 *           the loser gets 409 CONCURRENT_UPDATE. Null on a new object means
 *           "not saved yet" (Spring Data then inserts).
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

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
