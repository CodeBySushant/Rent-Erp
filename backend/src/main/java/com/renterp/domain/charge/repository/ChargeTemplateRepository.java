package com.renterp.domain.charge.repository;

import com.renterp.domain.charge.entity.ChargeTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChargeTemplateRepository extends JpaRepository<ChargeTemplate, UUID> {

    Page<ChargeTemplate> findByPropertyId(UUID propertyId, Pageable pageable);

    boolean existsByPropertyIdAndName(UUID propertyId, String name);

    // Billing engine: active charge templates for a property (pass 1 bills active only;
    // deactivated-charge proration B7 lands in pass 2).
    List<ChargeTemplate> findByPropertyIdAndActiveTrue(UUID propertyId);
}
