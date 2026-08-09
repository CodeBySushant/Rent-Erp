package com.renterp.domain.meter.repository;

import com.renterp.domain.meter.entity.InfrastructureMeterScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InfrastructureMeterScopeRepository extends JpaRepository<InfrastructureMeterScope, UUID> {

    List<InfrastructureMeterScope> findByMeterIdOrderByCreatedAtAsc(UUID meterId);

    boolean existsByMeterIdAndFloorId(UUID meterId, UUID floorId);
}
