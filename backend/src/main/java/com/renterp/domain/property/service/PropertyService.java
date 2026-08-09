package com.renterp.domain.property.service;

import com.renterp.common.exception.InvalidOperationException;
import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.domain.property.dto.CreatePropertyRequest;
import com.renterp.domain.property.dto.PropertyResponse;
import com.renterp.domain.property.dto.UpdatePropertyRequest;
import com.renterp.domain.auth.repository.UserRepository;
import com.renterp.domain.meter.repository.MeterRepository;
import com.renterp.domain.property.entity.Property;
import com.renterp.domain.property.entity.Property.ElectricityBillingMode;
import com.renterp.domain.property.repository.PropertyRepository;
import com.renterp.domain.propertyaccess.service.PropertyAccessService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PropertyService {

    private static final Logger log = LogManager.getLogger(PropertyService.class);

    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final PropertyAccessService propertyAccessService;
    private final MeterRepository meterRepository;

    public PropertyService(PropertyRepository propertyRepository, UserRepository userRepository,
                            PropertyAccessService propertyAccessService,
                            MeterRepository meterRepository) {
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.propertyAccessService = propertyAccessService;
        this.meterRepository = meterRepository;
    }

    // ── Create ─────────────────────────────────────────────────────────────────

    @Transactional
    public PropertyResponse createProperty(CreatePropertyRequest request) {
        log.debug("Creating property — owner: {}, name: {}", request.getOwnerUserId(), request.getName());

        // Checked explicitly rather than left to the DB FK constraint — without this,
        // a bad ownerUserId falls through to GlobalExceptionHandler's generic 500
        // catch-all instead of a clean 404.
        if (!userRepository.existsById(request.getOwnerUserId())) {
            log.warn("Property creation failed — owner user not found: {}", request.getOwnerUserId());
            throw new ResourceNotFoundException("User", "id", request.getOwnerUserId());
        }

        Property property = Property.builder()
                .ownerUserId(request.getOwnerUserId())
                .name(request.getName())
                .address(request.getAddress())
                .city(request.getCity())
                .electricityBillingMode(request.getElectricityBillingMode())
                .waterMode(request.getWaterMode())
                .defaultSplitRule(request.getDefaultSplitRule())
                .neaTariffMode(request.getNeaTariffMode())
                .midMonthDepartureRateMode(request.getMidMonthDepartureRateMode())
                .billingDay(request.getBillingDay())
                .gracePeriodDays(request.getGracePeriodDays())
                .paymentModelDefault(request.getPaymentModelDefault())
                .vacancyNoticePeriodDays(request.getVacancyNoticePeriodDays())
                .moveOutDayChargeable(request.isMoveOutDayChargeable())
                .commonUnitsChargedToTenants(request.isCommonUnitsChargedToTenants())
                .penaltyType(request.getPenaltyType())
                .penaltyFrequency(request.getPenaltyFrequency())
                .penaltyGraceDays(request.getPenaltyGraceDays())
                .penaltyCapAmount(request.getPenaltyCapAmount())
                .lateDepartureChargeType(request.getLateDepartureChargeType())
                .lateDepartureChargeAmount(request.getLateDepartureChargeAmount())
                .roundingMethod(request.getRoundingMethod())
                .roundingRemainderTo(request.getRoundingRemainderTo())
                .overageThresholdPercent(request.getOverageThresholdPercent())
                .overageAction(request.getOverageAction())
                .tdsEnabled(request.isTdsEnabled())
                .tdsRatePercent(request.getTdsRatePercent())
                .disputeWindowDays(request.getDisputeWindowDays())
                .build();

        Property saved = propertyRepository.save(property);
        log.info("Property created successfully — id: {}, owner: {}, name: {}", saved.getId(), saved.getOwnerUserId(), saved.getName());

        // Every property gets an OWNER row in property_access alongside owner_user_id, so
        // permission checks have one source of truth (spec §4.2/§20.1) — see PropertyAccessService.
        propertyAccessService.createOwnerGrant(saved.getId(), saved.getOwnerUserId());

        return PropertyResponse.from(saved);
    }

    // ── Read single ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PropertyResponse getPropertyById(UUID id) {
        log.debug("Fetching property by id: {}", id);

        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Property not found — id: {}", id);
                    return new ResourceNotFoundException("Property", "id", id);
                });

        log.debug("Property fetched — id: {}, name: {}", property.getId(), property.getName());
        return PropertyResponse.from(property);
    }

    // ── Read all (paginated, optionally filtered by owner) ────────────────────

    @Transactional(readOnly = true)
    public Page<PropertyResponse> getAllProperties(UUID ownerUserId, Pageable pageable) {
        log.debug("Fetching properties — owner: {}, page: {}, size: {}", ownerUserId, pageable.getPageNumber(), pageable.getPageSize());

        Page<Property> page = (ownerUserId != null)
                ? propertyRepository.findByOwnerUserId(ownerUserId, pageable)
                : propertyRepository.findAll(pageable);

        Page<PropertyResponse> response = page.map(PropertyResponse::from);
        log.debug("Properties fetched — total: {}, page: {}/{}", response.getTotalElements(), response.getNumber() + 1, response.getTotalPages());
        return response;
    }

    // ── Update ─────────────────────────────────────────────────────────────────

    @Transactional
    public PropertyResponse updateProperty(UUID id, UpdatePropertyRequest request) {
        log.debug("Updating property — id: {}", id);

        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Update failed — property not found — id: {}", id);
                    return new ResourceNotFoundException("Property", "id", id);
                });

        if (request.getName() != null) {
            property.setName(request.getName());
        }
        if (request.getAddress() != null) {
            property.setAddress(request.getAddress());
        }
        if (request.getCity() != null) {
            property.setCity(request.getCity());
        }
        if (request.getElectricityBillingMode() != null
                && request.getElectricityBillingMode() != property.getElectricityBillingMode()) {
            // Spec §6.5 gate — switching TO a metered mode requires at least one active meter to
            // already exist for this property. Without it, the property would enter a metered
            // billing mode with nothing to read against and the first billing run would silently
            // no-op. The reciprocal switch (metered → non-metered) is deliberately not gated;
            // meters just become inert. Opening-reading enforcement (the second half of §6.5)
            // waits on MeterReadingController.
            ElectricityBillingMode next = request.getElectricityBillingMode();
            boolean switchingToMetered =
                    (next == ElectricityBillingMode.SUB_METERED || next == ElectricityBillingMode.MAIN_METER_ONLY);
            if (switchingToMetered && !meterRepository.existsByPropertyIdAndActive(property.getId(), true)) {
                throw new InvalidOperationException(
                        "Cannot switch electricity billing mode to " + next + " — no active meter exists for this property (spec §6.5)");
            }
            property.setElectricityBillingMode(next);
        }
        if (request.getWaterMode() != null) {
            property.setWaterMode(request.getWaterMode());
        }
        if (request.getDefaultSplitRule() != null) {
            property.setDefaultSplitRule(request.getDefaultSplitRule());
        }
        if (request.getNeaTariffMode() != null) {
            property.setNeaTariffMode(request.getNeaTariffMode());
        }
        if (request.getMidMonthDepartureRateMode() != null) {
            property.setMidMonthDepartureRateMode(request.getMidMonthDepartureRateMode());
        }
        if (request.getBillingDay() != null) {
            property.setBillingDay(request.getBillingDay());
        }
        if (request.getGracePeriodDays() != null) {
            property.setGracePeriodDays(request.getGracePeriodDays());
        }
        if (request.getPaymentModelDefault() != null) {
            property.setPaymentModelDefault(request.getPaymentModelDefault());
        }
        if (request.getVacancyNoticePeriodDays() != null) {
            property.setVacancyNoticePeriodDays(request.getVacancyNoticePeriodDays());
        }
        if (request.getMoveOutDayChargeable() != null) {
            property.setMoveOutDayChargeable(request.getMoveOutDayChargeable());
        }
        if (request.getCommonUnitsChargedToTenants() != null) {
            property.setCommonUnitsChargedToTenants(request.getCommonUnitsChargedToTenants());
        }
        if (request.getPenaltyType() != null) {
            property.setPenaltyType(request.getPenaltyType());
        }
        if (request.getPenaltyFrequency() != null) {
            property.setPenaltyFrequency(request.getPenaltyFrequency());
        }
        if (request.getPenaltyGraceDays() != null) {
            property.setPenaltyGraceDays(request.getPenaltyGraceDays());
        }
        if (request.getPenaltyCapAmount() != null) {
            property.setPenaltyCapAmount(request.getPenaltyCapAmount());
        }
        if (request.getLateDepartureChargeType() != null) {
            property.setLateDepartureChargeType(request.getLateDepartureChargeType());
        }
        if (request.getLateDepartureChargeAmount() != null) {
            property.setLateDepartureChargeAmount(request.getLateDepartureChargeAmount());
        }
        if (request.getRoundingMethod() != null) {
            property.setRoundingMethod(request.getRoundingMethod());
        }
        if (request.getRoundingRemainderTo() != null) {
            property.setRoundingRemainderTo(request.getRoundingRemainderTo());
        }
        if (request.getOverageThresholdPercent() != null) {
            property.setOverageThresholdPercent(request.getOverageThresholdPercent());
        }
        if (request.getOverageAction() != null) {
            property.setOverageAction(request.getOverageAction());
        }
        if (request.getTdsEnabled() != null) {
            property.setTdsEnabled(request.getTdsEnabled());
        }
        if (request.getTdsRatePercent() != null) {
            property.setTdsRatePercent(request.getTdsRatePercent());
        }
        if (request.getDisputeWindowDays() != null) {
            property.setDisputeWindowDays(request.getDisputeWindowDays());
        }

        // saveAndFlush so JPA auditing's @LastModifiedDate lands on the managed entity
        // before the response DTO is built — see UserService.updateUser for why.
        Property updated = propertyRepository.saveAndFlush(property);
        log.info("Property updated successfully — id: {}", updated.getId());

        return PropertyResponse.from(updated);
    }

    // ── Soft Delete ────────────────────────────────────────────────────────────

    @Transactional
    public void deleteProperty(UUID id) {
        log.debug("Soft deleting property — id: {}", id);

        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Delete failed — property not found — id: {}", id);
                    return new ResourceNotFoundException("Property", "id", id);
                });

        if (!property.isActive()) {
            log.warn("Delete skipped — property already inactive — id: {}", id);
            return;
        }

        property.setActive(false);
        propertyRepository.saveAndFlush(property);
        log.info("Property soft deleted — id: {}", id);
    }
}
