package com.renterp.domain.auth.repository;

import com.renterp.domain.auth.entity.UserSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    Optional<UserSession> findByTokenHash(String tokenHash);

    // Row-locked read used when rotating a refresh token, so the same refresh
    // token cannot be exchanged twice by two concurrent requests.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UserSession s where s.tokenHash = :tokenHash")
    Optional<UserSession> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    // The access-token filter checks the session on every request: revoked or
    // expired sessions stop working immediately, not when the JWT expires.
    @Query("select (count(s) > 0) from UserSession s where s.id = :id and s.user.id = :userId "
            + "and s.revokedAt is null and s.expiresAt > :now")
    boolean isActive(@Param("id") UUID id, @Param("userId") UUID userId, @Param("now") Instant now);

    @Query("select s from UserSession s where s.user.id = :userId and s.revokedAt is null "
            + "and s.expiresAt > :now order by s.createdAt desc")
    List<UserSession> findActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    // Revoke all sessions for a user (e.g. password change, suspicious activity)
    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :now WHERE s.user.id = :userId AND s.revokedAt IS NULL")
    int revokeAllByUserId(UUID userId, Instant now);
}
