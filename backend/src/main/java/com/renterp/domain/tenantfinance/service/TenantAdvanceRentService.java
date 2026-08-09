package com.renterp.domain.tenantfinance.service;

import com.renterp.common.exception.InvalidOperationException;
import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership.MembershipStatus;
import com.renterp.domain.tenancy.repository.TenantPropertyMembershipRepository;
import com.renterp.domain.tenantfinance.dto.AdvanceRentResponse;
import com.renterp.domain.tenantfinance.dto.CreateAdvanceRentRequest;
import com.renterp.domain.tenantfinance.entity.TenantAdvanceRent;
import com.renterp.domain.tenantfinance.entity.TenantAdvanceRent.AdvanceStatus;
import com.renterp.domain.tenantfinance.repository.TenantAdvanceRentRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TenantAdvanceRentService {

    private static final Logger log = LogManager.getLogger(TenantAdvanceRentService.class);

    private final TenantAdvanceRentRepository advanceRepository;
    private final TenantPropertyMembershipRepository membershipRepository;

    public TenantAdvanceRentService(TenantAdvanceRentRepository advanceRepository,
                                     TenantPropertyMembershipRepository membershipRepository) {
        this.advanceRepository = advanceRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional
    public AdvanceRentResponse create(UUID membershipId, CreateAdvanceRentRequest req) {
        TenantPropertyMembership m = requireMembership(membershipId);
        if (m.getStatus() == MembershipStatus.TERMINATED) {
            throw new InvalidOperationException("Cannot record advance rent on a TERMINATED membership");
        }
        if (req.getCoveredFromBs().compareTo(req.getCoveredToBs()) > 0) {
            throw new InvalidOperationException("coveredFromBs must not be after coveredToBs");
        }
        TenantAdvanceRent a = TenantAdvanceRent.builder()
                .membershipId(membershipId)
                .amount(req.getAmount())
                .monthsCovered(req.getMonthsCovered())
                .coveredFromBs(req.getCoveredFromBs())
                .coveredToBs(req.getCoveredToBs())
                .status(AdvanceStatus.HELD)
                .notes(req.getNotes())
                .build();
        log.info("Advance rent recorded — membership: {}, amount: {}, months: {}",
                membershipId, req.getAmount(), req.getMonthsCovered());
        return AdvanceRentResponse.from(advanceRepository.saveAndFlush(a));
    }

    @Transactional(readOnly = true)
    public List<AdvanceRentResponse> listForMembership(UUID membershipId) {
        requireMembership(membershipId);
        return advanceRepository.findByMembershipIdOrderByCoveredFromBsAsc(membershipId)
                .stream().map(AdvanceRentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AdvanceRentResponse getById(UUID id) {
        return AdvanceRentResponse.from(advanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TenantAdvanceRent", "id", id)));
    }

    private TenantPropertyMembership requireMembership(UUID id) {
        return membershipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TenantPropertyMembership", "id", id));
    }
}
