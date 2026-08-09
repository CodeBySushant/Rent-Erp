package com.renterp.domain.tenancy.service;

import com.renterp.common.exception.DuplicateResourceException;
import com.renterp.common.exception.InvalidOperationException;
import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.domain.property.repository.PropertyRepository;
import com.renterp.domain.tenancy.dto.BlockTenantRequest;
import com.renterp.domain.tenancy.dto.BlockedTenantResponse;
import com.renterp.domain.tenancy.entity.PropertyBlockedTenant;
import com.renterp.domain.tenancy.repository.PropertyBlockedTenantRepository;
import com.renterp.domain.tenancy.repository.TenantProfileRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BlockedTenantService {

    private static final Logger log = LogManager.getLogger(BlockedTenantService.class);

    private final PropertyBlockedTenantRepository blockRepository;
    private final PropertyRepository propertyRepository;
    private final TenantProfileRepository tenantProfileRepository;

    public BlockedTenantService(PropertyBlockedTenantRepository blockRepository,
                                 PropertyRepository propertyRepository,
                                 TenantProfileRepository tenantProfileRepository) {
        this.blockRepository = blockRepository;
        this.propertyRepository = propertyRepository;
        this.tenantProfileRepository = tenantProfileRepository;
    }

    @Transactional
    public BlockedTenantResponse block(UUID propertyId, BlockTenantRequest req) {
        if (!propertyRepository.existsById(propertyId)) throw new ResourceNotFoundException("Property", "id", propertyId);
        if (!tenantProfileRepository.existsById(req.getTenantProfileId())) {
            throw new ResourceNotFoundException("TenantProfile", "id", req.getTenantProfileId());
        }
        if (blockRepository.existsByPropertyIdAndTenantProfileIdAndActive(propertyId, req.getTenantProfileId(), true)) {
            throw new DuplicateResourceException("PropertyBlockedTenant",
                    "propertyId+tenantProfileId (active)", propertyId + "+" + req.getTenantProfileId());
        }
        PropertyBlockedTenant b = PropertyBlockedTenant.builder()
                .propertyId(propertyId)
                .tenantProfileId(req.getTenantProfileId())
                .reason(req.getReason())
                .build();
        return BlockedTenantResponse.from(blockRepository.saveAndFlush(b));
    }

    @Transactional(readOnly = true)
    public List<BlockedTenantResponse> listActive(UUID propertyId) {
        if (!propertyRepository.existsById(propertyId)) throw new ResourceNotFoundException("Property", "id", propertyId);
        return blockRepository.findByPropertyIdAndActiveOrderByBlockedAtDesc(propertyId, true)
                .stream().map(BlockedTenantResponse::from).toList();
    }

    @Transactional
    public void unblock(UUID propertyId, UUID tenantProfileId) {
        PropertyBlockedTenant b = blockRepository
                .findByPropertyIdAndTenantProfileIdAndActive(propertyId, tenantProfileId, true)
                .orElseThrow(() -> new InvalidOperationException(
                        "Tenant " + tenantProfileId + " is not currently blocked on property " + propertyId));
        b.setActive(false);
        blockRepository.saveAndFlush(b);
        log.info("Tenant {} unblocked from property {}", tenantProfileId, propertyId);
    }

    // Used by JoinRequestService — no throw, just a boolean.
    public boolean isBlocked(UUID propertyId, UUID tenantProfileId) {
        return blockRepository.existsByPropertyIdAndTenantProfileIdAndActive(propertyId, tenantProfileId, true);
    }
}
