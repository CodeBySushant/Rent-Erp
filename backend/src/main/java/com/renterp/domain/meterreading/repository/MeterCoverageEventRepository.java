package com.renterp.domain.meterreading.repository;

import com.renterp.domain.meterreading.entity.MeterCoverageEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MeterCoverageEventRepository extends JpaRepository<MeterCoverageEvent, UUID> {
    Page<MeterCoverageEvent> findByPropertyId(UUID propertyId, Pageable pageable);
}
