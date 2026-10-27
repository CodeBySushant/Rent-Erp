package com.renterp.domain.notification.repository;

import com.renterp.domain.notification.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByUserId(UUID userId);

    // Push tokens are delivery addresses, not records: removing them on logout
    // or account deletion stops pushes to a device nobody is signed in on (M12).
    @Modifying
    @Query("delete from DeviceToken d where d.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from DeviceToken d where d.sessionId = :sessionId")
    int deleteBySessionId(@Param("sessionId") UUID sessionId);

    @Modifying
    @Query("delete from DeviceToken d where d.token = :token and d.userId = :userId")
    int deleteByTokenAndUserId(@Param("token") String token, @Param("userId") UUID userId);
}
