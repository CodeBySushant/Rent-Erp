package com.renterp.domain.charge.service;

import com.renterp.common.exception.DuplicateResourceException;
import com.renterp.common.exception.InvalidOperationException;
import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.domain.charge.dto.ChargeTemplateResponse;
import com.renterp.domain.charge.dto.CreateChargeTemplateRequest;
import com.renterp.domain.charge.dto.UpdateChargeTemplateRequest;
import com.renterp.domain.charge.entity.ChargeTemplate;
import com.renterp.domain.charge.entity.ChargeTemplate.DeactivationMode;
import com.renterp.domain.charge.repository.ChargeTemplateRepository;
import com.renterp.domain.property.repository.PropertyRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class ChargeTemplateService {

    private static final Logger log = LogManager.getLogger(ChargeTemplateService.class);

    private final ChargeTemplateRepository chargeTemplateRepository;
    private final PropertyRepository propertyRepository;

    public ChargeTemplateService(ChargeTemplateRepository chargeTemplateRepository,
                                  PropertyRepository propertyRepository) {
        this.chargeTemplateRepository = chargeTemplateRepository;
        this.propertyRepository = propertyRepository;
    }

    // ── Create ─────────────────────────────────────────────────────────────────

    @Transactional
    public ChargeTemplateResponse createChargeTemplate(CreateChargeTemplateRequest request) {
        log.debug("Creating charge template — property: {}, name: {}, amount: {}",
                request.getPropertyId(), request.getName(), request.getAmount());

        if (!propertyRepository.existsById(request.getPropertyId())) {
            log.warn("Charge template creation failed — property not found: {}", request.getPropertyId());
            throw new ResourceNotFoundException("Property", "id", request.getPropertyId());
        }
        if (chargeTemplateRepository.existsByPropertyIdAndName(request.getPropertyId(), request.getName())) {
            log.warn("Charge template creation failed — name '{}' already exists for property {}",
                    request.getName(), request.getPropertyId());
            throw new DuplicateResourceException("ChargeTemplate", "propertyId+name",
                    request.getPropertyId() + "+" + request.getName());
        }
        requireZeroAmountAcknowledgement(request.getAmount(), request.isZeroAmountAcknowledged());

        ChargeTemplate template = ChargeTemplate.builder()
                .propertyId(request.getPropertyId())
                .name(request.getName())
                .amount(request.getAmount())
                .splitBasis(request.getSplitBasis())
                .zeroAmountAcknowledged(request.isZeroAmountAcknowledged())
                .build();

        ChargeTemplate saved = chargeTemplateRepository.save(template);
        log.info("Charge template created successfully — id: {}, property: {}, name: {}",
                saved.getId(), saved.getPropertyId(), saved.getName());

        return ChargeTemplateResponse.from(saved);
    }

    // ── Read single ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ChargeTemplateResponse getChargeTemplateById(UUID id) {
        log.debug("Fetching charge template by id: {}", id);

        ChargeTemplate template = chargeTemplateRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Charge template not found — id: {}", id);
                    return new ResourceNotFoundException("ChargeTemplate", "id", id);
                });

        return ChargeTemplateResponse.from(template);
    }

    // ── Read all (paginated, optionally filtered by property) ──────────────────

    @Transactional(readOnly = true)
    public Page<ChargeTemplateResponse> getAllChargeTemplates(UUID propertyId, Pageable pageable) {
        log.debug("Fetching charge templates — property: {}, page: {}, size: {}",
                propertyId, pageable.getPageNumber(), pageable.getPageSize());

        Page<ChargeTemplate> page = (propertyId != null)
                ? chargeTemplateRepository.findByPropertyId(propertyId, pageable)
                : chargeTemplateRepository.findAll(pageable);

        return page.map(ChargeTemplateResponse::from);
    }

    // ── Update ─────────────────────────────────────────────────────────────────

    @Transactional
    public ChargeTemplateResponse updateChargeTemplate(UUID id, UpdateChargeTemplateRequest request) {
        log.debug("Updating charge template — id: {}", id);

        ChargeTemplate template = chargeTemplateRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Update failed — charge template not found — id: {}", id);
                    return new ResourceNotFoundException("ChargeTemplate", "id", id);
                });

        if (request.getName() != null && !request.getName().equals(template.getName())) {
            if (chargeTemplateRepository.existsByPropertyIdAndName(template.getPropertyId(), request.getName())) {
                log.warn("Update failed — name '{}' already exists for property {}",
                        request.getName(), template.getPropertyId());
                throw new DuplicateResourceException("ChargeTemplate", "propertyId+name",
                        template.getPropertyId() + "+" + request.getName());
            }
            template.setName(request.getName());
        }

        if (request.getAmount() != null) {
            boolean acknowledged = request.getZeroAmountAcknowledged() != null
                    ? request.getZeroAmountAcknowledged()
                    : template.isZeroAmountAcknowledged();
            requireZeroAmountAcknowledgement(request.getAmount(), acknowledged);
            template.setAmount(request.getAmount());
            template.setZeroAmountAcknowledged(acknowledged);
        }

        if (request.getSplitBasis() != null) {
            template.setSplitBasis(request.getSplitBasis());
        }

        // saveAndFlush so JPA auditing's @LastModifiedDate lands on the managed entity
        // before the response DTO is built — see PropertyService.updateProperty for why.
        ChargeTemplate updated = chargeTemplateRepository.saveAndFlush(template);
        log.info("Charge template updated successfully — id: {}", updated.getId());

        return ChargeTemplateResponse.from(updated);
    }

    // ── Soft Delete (deactivate) ─────────────────────────────────────────────────

    // Spec §14.2 B7 — deactivation always captures how the Billing engine (Phase 5, not
    // yet built) should treat the charge on the next run: prorated this cycle, applied
    // normally through next cycle then stopping, or voided entirely from this point.
    @Transactional
    public void deactivateChargeTemplate(UUID id, DeactivationMode deactivationMode) {
        log.debug("Deactivating charge template — id: {}, mode: {}", id, deactivationMode);

        ChargeTemplate template = chargeTemplateRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Deactivate failed — charge template not found — id: {}", id);
                    return new ResourceNotFoundException("ChargeTemplate", "id", id);
                });

        if (!template.isActive()) {
            log.warn("Deactivate skipped — charge template already inactive — id: {}", id);
            return;
        }

        template.setActive(false);
        template.setDeactivationMode(deactivationMode);
        chargeTemplateRepository.saveAndFlush(template);
        log.info("Charge template deactivated — id: {}, mode: {}", id, deactivationMode);
    }

    // ── Shared validation ──────────────────────────────────────────────────────

    private void requireZeroAmountAcknowledgement(BigDecimal amount, boolean acknowledged) {
        if (amount.compareTo(BigDecimal.ZERO) == 0 && !acknowledged) {
            throw new InvalidOperationException(
                    "Charge amount is zero — set zeroAmountAcknowledged=true to confirm this is intentional");
        }
    }
}
