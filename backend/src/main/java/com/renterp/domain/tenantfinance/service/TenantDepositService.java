package com.renterp.domain.tenantfinance.service;

import com.renterp.common.exception.DuplicateResourceException;
import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership.MembershipStatus;
import com.renterp.domain.tenancy.repository.TenantPropertyMembershipRepository;
import com.renterp.domain.tenantfinance.dto.CreateDepositRequest;
import com.renterp.domain.tenantfinance.dto.DepositResponse;
import com.renterp.domain.tenantfinance.dto.UpdateDepositRequest;
import com.renterp.domain.tenantfinance.entity.TenantDeposit;
import com.renterp.domain.tenantfinance.entity.TenantDeposit.DepositStatus;
import com.renterp.domain.tenantfinance.repository.TenantDepositRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TenantDepositService {

    private static final Logger log = LogManager.getLogger(TenantDepositService.class);

    private final TenantDepositRepository depositRepository;
    private final TenantPropertyMembershipRepository membershipRepository;

    public TenantDepositService(TenantDepositRepository depositRepository,
                                 TenantPropertyMembershipRepository membershipRepository) {
        this.depositRepository = depositRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional
    public DepositResponse create(UUID membershipId, CreateDepositRequest req) {
        TenantPropertyMembership m = requireMembership(membershipId);
        // Depositing while TERMINATED would leave money attached to a closed relationship.
        // Vacancy (Phase 7) is the only path that touches a terminated tenancy's deposit.
        if (m.getStatus() == MembershipStatus.TERMINATED) {
            throw new com.renterp.common.exception.InvalidOperationException(
                    "Cannot record a deposit on a TERMINATED membership");
        }
        if (depositRepository.existsByMembershipId(membershipId)) {
            throw new DuplicateResourceException("TenantDeposit", "membershipId", membershipId);
        }
        TenantDeposit d = TenantDeposit.builder()
                .membershipId(membershipId)
                .amount(req.getAmount())
                .currency(req.getCurrency() != null ? req.getCurrency() : "NPR")
                .status(DepositStatus.HELD)
                .receivedAtBs(req.getReceivedAtBs())
                .notes(req.getNotes())
                .build();
        log.info("Deposit created — membership: {}, amount: {}", membershipId, req.getAmount());
        return DepositResponse.from(depositRepository.saveAndFlush(d));
    }

    @Transactional(readOnly = true)
    public DepositResponse get(UUID membershipId) {
        requireMembership(membershipId);
        return depositRepository.findByMembershipId(membershipId)
                .map(DepositResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("TenantDeposit", "membershipId", membershipId));
    }

    @Transactional
    public DepositResponse update(UUID membershipId, UpdateDepositRequest req) {
        TenantDeposit d = depositRepository.findByMembershipId(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("TenantDeposit", "membershipId", membershipId));
        // Only HELD deposits are correctable — post-vacancy status transitions freeze the row.
        if (d.getStatus() != DepositStatus.HELD) {
            throw new com.renterp.common.exception.InvalidOperationException(
                    "Only HELD deposits can be corrected — current status: " + d.getStatus());
        }
        if (req.getAmount() != null) d.setAmount(req.getAmount());
        if (req.getReceivedAtBs() != null) d.setReceivedAtBs(req.getReceivedAtBs());
        if (req.getNotes() != null) d.setNotes(req.getNotes());
        return DepositResponse.from(depositRepository.saveAndFlush(d));
    }

    private TenantPropertyMembership requireMembership(UUID id) {
        return membershipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TenantPropertyMembership", "id", id));
    }
}
