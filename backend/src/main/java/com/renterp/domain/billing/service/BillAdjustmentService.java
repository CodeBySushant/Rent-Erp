package com.renterp.domain.billing.service;

import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.domain.billing.dto.AdjustmentResponse;
import com.renterp.domain.billing.dto.CreateAdjustmentRequest;
import com.renterp.domain.billing.entity.TenantBillAdjustment;
import com.renterp.domain.billing.repository.TenantBillAdjustmentRepository;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership;
import com.renterp.domain.tenancy.repository.TenantPropertyMembershipRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * One-time carry-forward charges/credits that follow a tenant onto their next bill, incl.
 * the vacancy bill (P9). The billing engine consumes PENDING rows at generation.
 */
@Service
public class BillAdjustmentService {

    private static final Logger log = LogManager.getLogger(BillAdjustmentService.class);

    private final TenantBillAdjustmentRepository adjustmentRepository;
    private final TenantPropertyMembershipRepository membershipRepository;

    public BillAdjustmentService(TenantBillAdjustmentRepository adjustmentRepository,
                                 TenantPropertyMembershipRepository membershipRepository) {
        this.adjustmentRepository = adjustmentRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional
    public AdjustmentResponse create(UUID membershipId, CreateAdjustmentRequest req) {
        TenantPropertyMembership m = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("TenantPropertyMembership", "id", membershipId));
        TenantBillAdjustment a = TenantBillAdjustment.builder()
                .membershipId(membershipId)
                .propertyId(m.getPropertyId())
                .adjustmentType(req.getAdjustmentType())
                .source(TenantBillAdjustment.Source.ONE_TIME)
                .amount(req.getAmount())
                .reason(req.getReason())
                .status(TenantBillAdjustment.Status.PENDING)
                .createdBy(req.getCreatedBy())
                .build();
        TenantBillAdjustment saved = adjustmentRepository.saveAndFlush(a);
        log.info("One-time adjustment recorded — membership: {}, type: {}, amount: {}",
                membershipId, saved.getAdjustmentType(), saved.getAmount());
        return AdjustmentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<AdjustmentResponse> listForMembership(UUID membershipId) {
        if (!membershipRepository.existsById(membershipId)) {
            throw new ResourceNotFoundException("TenantPropertyMembership", "id", membershipId);
        }
        return adjustmentRepository.findByMembershipId(membershipId).stream()
                .map(AdjustmentResponse::from).toList();
    }
}
