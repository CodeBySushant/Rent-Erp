package com.renterp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA auditing app-wide.
 *
 * With this on, any entity annotated with {@code @EntityListeners(AuditingEntityListener.class)}
 * gets its {@code @CreatedDate} / {@code @LastModifiedDate} fields populated automatically —
 * no manual @PrePersist/@PreUpdate boilerplate per entity.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
