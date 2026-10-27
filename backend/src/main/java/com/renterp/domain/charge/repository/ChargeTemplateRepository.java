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

    /**
     * H14: the charges a billing period may still bill - active ones, plus ones
     * deactivated as THIS_CYCLE_PRORATED / NEXT_CYCLE on or after the period's
     * first day. VOID (and pre-V25 rows without a date) are never billed again.
     */
    @org.springframework.data.jpa.repository.Query("""
            select c from ChargeTemplate c
            where c.propertyId = :propertyId
              and (c.active = true
                   or (c.deactivationMode in :modes and c.deactivatedAtBs is not null
                       and c.deactivatedAtBs >= :periodStartBs))
            """)
    List<ChargeTemplate> findBillableInPeriod(
            @org.springframework.data.repository.query.Param("propertyId") UUID propertyId,
            @org.springframework.data.repository.query.Param("periodStartBs") String periodStartBs,
            @org.springframework.data.repository.query.Param("modes")
            java.util.Collection<ChargeTemplate.DeactivationMode> modes);
}
