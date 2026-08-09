package com.renterp.domain.meterreading.repository;

import com.renterp.domain.meterreading.entity.MeterCoverageEventChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MeterCoverageEventChangeRepository extends JpaRepository<MeterCoverageEventChange, UUID> {
    List<MeterCoverageEventChange> findByCoverageEventIdOrderByCreatedAtAsc(UUID coverageEventId);
    List<MeterCoverageEventChange> findByMeterIdOrderByCreatedAtDesc(UUID meterId);
}
