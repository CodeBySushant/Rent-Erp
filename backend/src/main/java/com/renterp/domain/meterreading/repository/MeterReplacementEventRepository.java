package com.renterp.domain.meterreading.repository;

import com.renterp.domain.meterreading.entity.MeterReplacementEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MeterReplacementEventRepository extends JpaRepository<MeterReplacementEvent, UUID> {
    List<MeterReplacementEvent> findByOldMeterIdOrNewMeterIdOrderByCreatedAtDesc(UUID oldMeterId, UUID newMeterId);
}
