package com.renterp.domain.billing.service;

import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.domain.billing.dto.TenantBillResponse;
import com.renterp.domain.billing.entity.TenantBill;
import com.renterp.domain.billing.repository.BillingRunRepository;
import com.renterp.domain.billing.repository.TenantBillRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TenantBillService {

    private final TenantBillRepository tenantBillRepository;
    private final BillingRunRepository billingRunRepository;

    public TenantBillService(TenantBillRepository tenantBillRepository,
                             BillingRunRepository billingRunRepository) {
        this.tenantBillRepository = tenantBillRepository;
        this.billingRunRepository = billingRunRepository;
    }

    @Transactional(readOnly = true)
    public TenantBillResponse getById(UUID id) {
        return TenantBillResponse.from(tenantBillRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TenantBill", "id", id)));
    }

    @Transactional(readOnly = true)
    public List<TenantBillResponse> listByRun(UUID runId) {
        if (!billingRunRepository.existsById(runId)) {
            throw new ResourceNotFoundException("BillingRun", "id", runId);
        }
        return tenantBillRepository.findByBillingRunId(runId).stream()
                .map(TenantBillResponse::from).toList();
    }

    @Transactional(readOnly = true)
    /** C7: {@code includeDrafts} false (a tenant) hides DRAFT bills - they are not sent yet. */
    public Page<TenantBillResponse> listByMembership(UUID membershipId, boolean includeDrafts, Pageable pageable) {
        Page<com.renterp.domain.billing.entity.TenantBill> page = includeDrafts
                ? tenantBillRepository.findByMembershipId(membershipId, pageable)
                : tenantBillRepository.findByMembershipIdAndStatusNot(membershipId,
                        com.renterp.domain.billing.entity.TenantBill.Status.DRAFT, pageable);
        return page.map(TenantBillResponse::from);
    }
}
