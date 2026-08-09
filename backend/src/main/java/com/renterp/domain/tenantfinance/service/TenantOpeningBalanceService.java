package com.renterp.domain.tenantfinance.service;

import com.renterp.common.exception.DuplicateResourceException;
import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.domain.tenancy.repository.TenantPropertyMembershipRepository;
import com.renterp.domain.tenantfinance.dto.CreateOpeningBalanceRequest;
import com.renterp.domain.tenantfinance.dto.OpeningBalanceResponse;
import com.renterp.domain.tenantfinance.entity.TenantOpeningBalance;
import com.renterp.domain.tenantfinance.repository.TenantOpeningBalanceRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TenantOpeningBalanceService {

    private static final Logger log = LogManager.getLogger(TenantOpeningBalanceService.class);

    private final TenantOpeningBalanceRepository openingRepository;
    private final TenantPropertyMembershipRepository membershipRepository;

    public TenantOpeningBalanceService(TenantOpeningBalanceRepository openingRepository,
                                        TenantPropertyMembershipRepository membershipRepository) {
        this.openingRepository = openingRepository;
        this.membershipRepository = membershipRepository;
    }

    // One-shot per membership. No update/delete surface — see DEVLOG for rationale.
    @Transactional
    public OpeningBalanceResponse create(UUID membershipId, CreateOpeningBalanceRequest req) {
        if (!membershipRepository.existsById(membershipId)) {
            throw new ResourceNotFoundException("TenantPropertyMembership", "id", membershipId);
        }
        if (openingRepository.existsByMembershipId(membershipId)) {
            throw new DuplicateResourceException("TenantOpeningBalance", "membershipId", membershipId);
        }
        TenantOpeningBalance b = TenantOpeningBalance.builder()
                .membershipId(membershipId)
                .amount(req.getAmount())
                .direction(req.getDirection())
                .asOfBs(req.getAsOfBs())
                .notes(req.getNotes())
                .build();
        log.info("Opening balance recorded — membership: {}, direction: {}, amount: {}",
                membershipId, req.getDirection(), req.getAmount());
        return OpeningBalanceResponse.from(openingRepository.saveAndFlush(b));
    }

    @Transactional(readOnly = true)
    public OpeningBalanceResponse get(UUID membershipId) {
        if (!membershipRepository.existsById(membershipId)) {
            throw new ResourceNotFoundException("TenantPropertyMembership", "id", membershipId);
        }
        return openingRepository.findByMembershipId(membershipId)
                .map(OpeningBalanceResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("TenantOpeningBalance", "membershipId", membershipId));
    }
}
