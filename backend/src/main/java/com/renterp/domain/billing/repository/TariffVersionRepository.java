package com.renterp.domain.billing.repository;

import com.renterp.domain.billing.entity.TariffVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TariffVersionRepository extends JpaRepository<TariffVersion, UUID> {

    Page<TariffVersion> findByActiveTrue(Pageable pageable);

    boolean existsByEffectiveFromBsAndActiveTrue(String effectiveFromBs);

    // The active tariff in force on a given BS date: the latest one whose effectiveFromBs
    // is on/before the date and whose effectiveToBs is null or on/after it. String
    // comparison is valid because BS dates are zero-padded "YYYY-MM-DD".
    @Query("""
            select t from TariffVersion t
            where t.active = true
              and t.effectiveFromBs <= :asOfBs
              and (t.effectiveToBs is null or t.effectiveToBs >= :asOfBs)
            order by t.effectiveFromBs desc
            """)
    List<TariffVersion> findEffectiveOn(@Param("asOfBs") String asOfBs, Pageable pageable);
}
