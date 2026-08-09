package com.renterp.domain.billing.repository;

import com.renterp.domain.billing.entity.BillCorrection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BillCorrectionRepository extends JpaRepository<BillCorrection, UUID> {

    List<BillCorrection> findByOriginalBillId(UUID originalBillId);

    List<BillCorrection> findByMembershipId(UUID membershipId);
}
