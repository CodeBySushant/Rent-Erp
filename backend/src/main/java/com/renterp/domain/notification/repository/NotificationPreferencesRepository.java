package com.renterp.domain.notification.repository;

import com.renterp.domain.notification.entity.NotificationPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotificationPreferencesRepository extends JpaRepository<NotificationPreferences, UUID> {
}
