package com.renterp.domain.meter.repository;

import com.renterp.domain.meter.entity.Meter;
import com.renterp.domain.meter.entity.Meter.MeterPurpose;
import com.renterp.domain.meter.entity.Meter.MeterType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MeterRepository extends JpaRepository<Meter, UUID> {

    Page<Meter> findByPropertyId(UUID propertyId, Pageable pageable);

    // Billing engine (pass 2): all active meters of a purpose for a property, non-paged.
    // The engine partitions the electricity list into MAIN (blended source) and TENANT_SUPPLY
    // (sub-meters) itself, so it fetches by purpose and filters by type in-memory.
    List<Meter> findByPropertyIdAndMeterPurposeAndActiveTrue(UUID propertyId, MeterPurpose meterPurpose);

    Page<Meter> findByPropertyIdAndMeterType(UUID propertyId, MeterType meterType, Pageable pageable);

    Page<Meter> findByPropertyIdAndMeterPurpose(UUID propertyId, MeterPurpose meterPurpose, Pageable pageable);

    Page<Meter> findByPropertyIdAndMeterTypeAndMeterPurpose(UUID propertyId, MeterType meterType, MeterPurpose meterPurpose, Pageable pageable);

    boolean existsByPropertyIdAndSerialNumber(UUID propertyId, String serialNumber);

    // Used by PropertyService §6.5 gate — block a switch TO SUB_METERED when no active meter exists.
    boolean existsByPropertyIdAndActive(UUID propertyId, boolean active);

    // C2 / M5: serialises reading submissions on one meter, so two submissions
    // cannot both pass the "one pending / one per month" checks.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Meter m where m.id = :id")
    Optional<Meter> findByIdForUpdate(@Param("id") UUID id);

    // H15: one active MAIN meter per property and purpose.
    boolean existsByPropertyIdAndMeterPurposeAndMeterTypeAndActiveTrue(
            UUID propertyId, MeterPurpose meterPurpose, MeterType meterType);

    // C8: a merged tenant profile keeps its designated meters.
    @Modifying
    @Query("update Meter m set m.designatedTenantId = :to, m.version = m.version + 1 where m.designatedTenantId = :from")
    int moveDesignatedTenant(@Param("from") UUID from, @Param("to") UUID to);
}
