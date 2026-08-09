package com.renterp.domain.meter.service;

import com.renterp.common.exception.DuplicateResourceException;
import com.renterp.common.exception.InvalidOperationException;
import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.domain.meter.dto.*;
import com.renterp.domain.meter.entity.InfrastructureMeterScope;
import com.renterp.domain.meter.entity.InfrastructureMeterScope.ScopeBy;
import com.renterp.domain.meter.entity.Meter;
import com.renterp.domain.meter.entity.Meter.MeterPurpose;
import com.renterp.domain.meter.entity.Meter.MeterStatus;
import com.renterp.domain.meter.entity.Meter.MeterType;
import com.renterp.domain.meter.entity.Meter.ReadingResponsibility;
import com.renterp.domain.meter.entity.MeterRoomCoverage;
import com.renterp.domain.meter.repository.InfrastructureMeterScopeRepository;
import com.renterp.domain.meter.repository.MeterRepository;
import com.renterp.domain.meter.repository.MeterRoomCoverageRepository;
import com.renterp.domain.property.repository.PropertyRepository;
import com.renterp.domain.structure.entity.Floor;
import com.renterp.domain.structure.repository.FloorRepository;
import com.renterp.domain.structure.repository.RoomRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MeterService {

    private static final Logger log = LogManager.getLogger(MeterService.class);

    private final MeterRepository meterRepository;
    private final MeterRoomCoverageRepository coverageRepository;
    private final InfrastructureMeterScopeRepository scopeRepository;
    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final FloorRepository floorRepository;

    public MeterService(MeterRepository meterRepository,
                         MeterRoomCoverageRepository coverageRepository,
                         InfrastructureMeterScopeRepository scopeRepository,
                         PropertyRepository propertyRepository,
                         RoomRepository roomRepository,
                         FloorRepository floorRepository) {
        this.meterRepository = meterRepository;
        this.coverageRepository = coverageRepository;
        this.scopeRepository = scopeRepository;
        this.propertyRepository = propertyRepository;
        this.roomRepository = roomRepository;
        this.floorRepository = floorRepository;
    }

    // ── Meter: Create ─────────────────────────────────────────────────────────

    @Transactional
    public MeterResponse createMeter(CreateMeterRequest request) {
        log.debug("Creating meter — property: {}, label: {}, type: {}, purpose: {}",
                request.getPropertyId(), request.getLabel(), request.getMeterType(), request.getMeterPurpose());

        if (!propertyRepository.existsById(request.getPropertyId())) {
            log.warn("Meter creation failed — property not found: {}", request.getPropertyId());
            throw new ResourceNotFoundException("Property", "id", request.getPropertyId());
        }

        // Cross-field: infrastructure_scope_type is required for INFRASTRUCTURE meters and
        // must be null otherwise. The V8 CHECK constraint enforces the same rule, but a
        // service-layer check surfaces a clean 400 instead of a DataIntegrityViolation.
        validateInfrastructureScopeType(request.getMeterType(), request.getInfrastructureScopeType());

        // Spec §14.1 M12 — property-scoped duplicate serial block. Serial is optional; skip
        // when null so we don't collide multiple no-serial meters against one another.
        if (request.getSerialNumber() != null && !request.getSerialNumber().isBlank()
                && meterRepository.existsByPropertyIdAndSerialNumber(request.getPropertyId(), request.getSerialNumber())) {
            log.warn("Meter creation failed — serial '{}' already used in property {}",
                    request.getSerialNumber(), request.getPropertyId());
            throw new DuplicateResourceException("Meter", "propertyId+serialNumber",
                    request.getPropertyId() + "+" + request.getSerialNumber());
        }

        // Spec §7.6 / §14.1 M18 — infra meters default to LANDLORD_ONLY when the landlord
        // didn't specify. For non-infra meters we require an explicit choice so the property
        // config surfaces the decision, rather than letting a silent default hide it.
        ReadingResponsibility responsibility = request.getReadingResponsibility();
        if (responsibility == null) {
            if (request.getMeterType() == MeterType.INFRASTRUCTURE) {
                responsibility = ReadingResponsibility.LANDLORD_ONLY;
            } else {
                throw new InvalidOperationException(
                        "readingResponsibility is required for non-infrastructure meters");
            }
        }

        Meter.MeterBuilder<?, ?> builder = Meter.builder()
                .propertyId(request.getPropertyId())
                .serialNumber(nullIfBlank(request.getSerialNumber()))
                .label(request.getLabel())
                .meterPurpose(request.getMeterPurpose())
                .meterType(request.getMeterType())
                .infrastructureScopeType(request.getInfrastructureScopeType())
                .splitRuleOverride(request.getSplitRuleOverride())
                .readingResponsibility(responsibility)
                .status(MeterStatus.ACTIVE);
        if (request.getMaxReadingValue() != null) {
            builder.maxReadingValue(request.getMaxReadingValue());
        }
        Meter meter = builder.build();

        Meter saved = meterRepository.save(meter);
        log.info("Meter created successfully — id: {}, property: {}, label: {}",
                saved.getId(), saved.getPropertyId(), saved.getLabel());

        return MeterResponse.from(saved);
    }

    // ── Meter: Read single ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MeterResponse getMeterById(UUID id) {
        log.debug("Fetching meter by id: {}", id);
        Meter meter = requireMeter(id);
        return MeterResponse.from(meter);
    }

    // ── Meter: Read all (paginated + filtered by property/type/purpose) ───────

    @Transactional(readOnly = true)
    public Page<MeterResponse> getAllMeters(UUID propertyId, MeterType meterType,
                                             MeterPurpose meterPurpose, Pageable pageable) {
        log.debug("Fetching meters — property: {}, type: {}, purpose: {}, page: {}/{}",
                propertyId, meterType, meterPurpose, pageable.getPageNumber(), pageable.getPageSize());

        Page<Meter> page;
        if (propertyId != null && meterType != null && meterPurpose != null) {
            page = meterRepository.findByPropertyIdAndMeterTypeAndMeterPurpose(propertyId, meterType, meterPurpose, pageable);
        } else if (propertyId != null && meterType != null) {
            page = meterRepository.findByPropertyIdAndMeterType(propertyId, meterType, pageable);
        } else if (propertyId != null && meterPurpose != null) {
            page = meterRepository.findByPropertyIdAndMeterPurpose(propertyId, meterPurpose, pageable);
        } else if (propertyId != null) {
            page = meterRepository.findByPropertyId(propertyId, pageable);
        } else {
            page = meterRepository.findAll(pageable);
        }

        return page.map(MeterResponse::from);
    }

    // ── Meter: Update ─────────────────────────────────────────────────────────

    @Transactional
    public MeterResponse updateMeter(UUID id, UpdateMeterRequest request) {
        log.debug("Updating meter — id: {}", id);

        Meter meter = requireMeter(id);

        // Serial-number edit: re-check property-scoped uniqueness. Only when the value
        // actually changes — an unchanged serial trips the uniqueness check on itself.
        if (request.getSerialNumber() != null) {
            String newSerial = nullIfBlank(request.getSerialNumber());
            if (newSerial != null && !newSerial.equals(meter.getSerialNumber())) {
                if (meterRepository.existsByPropertyIdAndSerialNumber(meter.getPropertyId(), newSerial)) {
                    log.warn("Update failed — serial '{}' already used in property {}",
                            newSerial, meter.getPropertyId());
                    throw new DuplicateResourceException("Meter", "propertyId+serialNumber",
                            meter.getPropertyId() + "+" + newSerial);
                }
            }
            meter.setSerialNumber(newSerial);
        }
        if (request.getLabel() != null) {
            meter.setLabel(request.getLabel());
        }
        if (request.getSplitRuleOverride() != null) {
            meter.setSplitRuleOverride(request.getSplitRuleOverride());
        }
        if (request.getReadingResponsibility() != null) {
            meter.setReadingResponsibility(request.getReadingResponsibility());
        }
        if (request.getMaxReadingValue() != null) {
            meter.setMaxReadingValue(request.getMaxReadingValue());
        }

        Meter updated = meterRepository.saveAndFlush(meter);
        log.info("Meter updated successfully — id: {}", updated.getId());
        return MeterResponse.from(updated);
    }

    // ── Meter: Soft delete (deactivation, spec §7.8 / §14.1 M11) ──────────────

    @Transactional
    public void deactivateMeter(UUID id) {
        log.debug("Deactivating meter — id: {}", id);

        Meter meter = requireMeter(id);

        if (!meter.isActive() || meter.getStatus() == MeterStatus.INACTIVE) {
            log.warn("Deactivate skipped — meter already inactive — id: {}", id);
            return;
        }

        // Spec §14.1 M11 partial — the "show affected tenants/rooms before confirming" UI check
        // lives on the client; the server-side rule is: block deactivation while any active
        // room coverage row exists (effectiveToBs IS NULL). The landlord must end coverage first,
        // which is the moment the UI can display "who loses coverage".
        if (coverageRepository.existsByMeterIdAndEffectiveToBsIsNull(id)) {
            log.warn("Deactivate blocked — meter {} still has active room coverage rows", id);
            throw new InvalidOperationException(
                    "Cannot deactivate meter while it has active room coverage — end coverage rows first (spec §14.1 M11)");
        }

        meter.setStatus(MeterStatus.INACTIVE);
        meter.setActive(false);
        meterRepository.saveAndFlush(meter);
        log.info("Meter deactivated — id: {}", id);
    }

    // ── Coverage: add ──────────────────────────────────────────────────────────

    @Transactional
    public MeterRoomCoverageResponse addRoomCoverage(UUID meterId, CreateMeterRoomCoverageRequest request) {
        log.debug("Adding room coverage — meter: {}, room: {}, from: {}",
                meterId, request.getRoomId(), request.getEffectiveFromBs());

        Meter meter = requireMeter(meterId);
        if (meter.getStatus() == MeterStatus.INACTIVE) {
            throw new InvalidOperationException("Cannot add coverage to an inactive meter");
        }
        // Main and infrastructure meters conceptually don't have room-scoped coverage — MAIN sits
        // upstream of all rooms and INFRASTRUCTURE covers pumps/pipes, not rooms. Coverage is a
        // TENANT_SUPPLY concept. Blocked here so the coverage table stays semantically clean.
        if (meter.getMeterType() != MeterType.TENANT_SUPPLY) {
            throw new InvalidOperationException(
                    "Room coverage only applies to TENANT_SUPPLY meters (spec §7.1) — got " + meter.getMeterType());
        }
        if (!roomRepository.existsById(request.getRoomId())) {
            throw new ResourceNotFoundException("Room", "id", request.getRoomId());
        }
        // Our own rule (mirrors the M4 chain-conflict logic used elsewhere): if this (meter,
        // room) pair already has an active row (effectiveToBs IS NULL), block. End the existing
        // row first, then add a new one — same shape as reading-chain events, no overlapping
        // periods on the same physical link.
        Optional<MeterRoomCoverage> currentActive =
                coverageRepository.findFirstByMeterIdAndRoomIdAndEffectiveToBsIsNull(meterId, request.getRoomId());
        if (currentActive.isPresent()) {
            throw new DuplicateResourceException("MeterRoomCoverage",
                    "meterId+roomId (active)", meterId + "+" + request.getRoomId());
        }

        MeterRoomCoverage coverage = MeterRoomCoverage.builder()
                .meterId(meterId)
                .roomId(request.getRoomId())
                .effectiveFromBs(request.getEffectiveFromBs())
                .build();

        MeterRoomCoverage saved = coverageRepository.save(coverage);
        log.info("Room coverage created — id: {}, meter: {}, room: {}",
                saved.getId(), meterId, request.getRoomId());
        return MeterRoomCoverageResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<MeterRoomCoverageResponse> listRoomCoverage(UUID meterId) {
        if (!meterRepository.existsById(meterId)) {
            throw new ResourceNotFoundException("Meter", "id", meterId);
        }
        return coverageRepository.findByMeterIdOrderByEffectiveFromBsAsc(meterId).stream()
                .map(MeterRoomCoverageResponse::from)
                .toList();
    }

    @Transactional
    public MeterRoomCoverageResponse endRoomCoverage(UUID meterId, UUID coverageId,
                                                      EndMeterRoomCoverageRequest request) {
        log.debug("Ending room coverage — meter: {}, coverage: {}, to: {}",
                meterId, coverageId, request.getEffectiveToBs());

        MeterRoomCoverage coverage = coverageRepository.findById(coverageId)
                .orElseThrow(() -> new ResourceNotFoundException("MeterRoomCoverage", "id", coverageId));

        if (!coverage.getMeterId().equals(meterId)) {
            // Same shape as PropertyAccessController's cross-property protection — coverage
            // rows are namespaced under the parent meter in the URL, so a mismatch is a 404
            // rather than 400 (the resource "meter X's coverage Y" genuinely doesn't exist).
            throw new ResourceNotFoundException("MeterRoomCoverage", "id", coverageId);
        }
        if (coverage.getEffectiveToBs() != null) {
            throw new InvalidOperationException("Coverage row is already ended");
        }

        coverage.setEffectiveToBs(request.getEffectiveToBs());
        MeterRoomCoverage saved = coverageRepository.saveAndFlush(coverage);
        log.info("Room coverage ended — id: {}, to: {}", saved.getId(), saved.getEffectiveToBs());
        return MeterRoomCoverageResponse.from(saved);
    }

    // ── Infra scope: add / list / delete ──────────────────────────────────────

    @Transactional
    public InfrastructureMeterScopeResponse addInfrastructureScope(UUID meterId,
                                                                     CreateInfrastructureMeterScopeRequest request) {
        log.debug("Adding infra scope — meter: {}, scopeBy: {}, floor: {}, tenant: {}",
                meterId, request.getScopeBy(), request.getFloorId(), request.getTenantId());

        Meter meter = requireMeter(meterId);
        if (meter.getMeterType() != MeterType.INFRASTRUCTURE) {
            throw new InvalidOperationException(
                    "Infrastructure scope only applies to INFRASTRUCTURE meters — got " + meter.getMeterType());
        }
        if (meter.getInfrastructureScopeType() != Meter.InfrastructureScopeType.SCOPED) {
            throw new InvalidOperationException(
                    "Meter has infrastructureScopeType=GLOBAL — no scope rows are needed (or allowed)");
        }

        if (request.getScopeBy() == ScopeBy.FLOOR) {
            if (request.getFloorId() == null || request.getTenantId() != null) {
                throw new InvalidOperationException(
                        "scopeBy=FLOOR requires floorId and forbids tenantId");
            }
            Floor floor = floorRepository.findById(request.getFloorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Floor", "id", request.getFloorId()));
            if (!floor.getPropertyId().equals(meter.getPropertyId())) {
                throw new InvalidOperationException(
                        "Floor " + floor.getId() + " does not belong to meter's property " + meter.getPropertyId());
            }
            if (scopeRepository.existsByMeterIdAndFloorId(meterId, request.getFloorId())) {
                throw new DuplicateResourceException("InfrastructureMeterScope",
                        "meterId+floorId", meterId + "+" + request.getFloorId());
            }
        } else {
            // scopeBy = TENANT — deferred until TenancyController exists (Phase 4). No tenants
            // table means we can't validate the FK and we'd be storing dangling references.
            throw new InvalidOperationException(
                    "scopeBy=TENANT scoping is deferred until TenancyController (Phase 4) is built");
        }

        InfrastructureMeterScope scope = InfrastructureMeterScope.builder()
                .meterId(meterId)
                .scopeBy(request.getScopeBy())
                .floorId(request.getFloorId())
                .build();

        InfrastructureMeterScope saved = scopeRepository.save(scope);
        log.info("Infra scope created — id: {}, meter: {}, floor: {}",
                saved.getId(), meterId, request.getFloorId());
        return InfrastructureMeterScopeResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<InfrastructureMeterScopeResponse> listInfrastructureScope(UUID meterId) {
        if (!meterRepository.existsById(meterId)) {
            throw new ResourceNotFoundException("Meter", "id", meterId);
        }
        return scopeRepository.findByMeterIdOrderByCreatedAtAsc(meterId).stream()
                .map(InfrastructureMeterScopeResponse::from)
                .toList();
    }

    @Transactional
    public void deleteInfrastructureScope(UUID meterId, UUID scopeId) {
        log.debug("Deleting infra scope — meter: {}, scope: {}", meterId, scopeId);

        InfrastructureMeterScope scope = scopeRepository.findById(scopeId)
                .orElseThrow(() -> new ResourceNotFoundException("InfrastructureMeterScope", "id", scopeId));
        if (!scope.getMeterId().equals(meterId)) {
            throw new ResourceNotFoundException("InfrastructureMeterScope", "id", scopeId);
        }

        // Hard delete of the scope row is fine — scope is a live set, not a historical
        // ledger. Removing a floor from an infra meter's scope is the equivalent of
        // ending its assignment, and there is no history semantic on the row itself.
        scopeRepository.delete(scope);
        log.info("Infra scope deleted — id: {}", scopeId);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private Meter requireMeter(UUID id) {
        return meterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Meter", "id", id));
    }

    private static void validateInfrastructureScopeType(MeterType type, Meter.InfrastructureScopeType scope) {
        if (type == MeterType.INFRASTRUCTURE && scope == null) {
            throw new InvalidOperationException(
                    "infrastructureScopeType is required when meterType=INFRASTRUCTURE (GLOBAL or SCOPED)");
        }
        if (type != MeterType.INFRASTRUCTURE && scope != null) {
            throw new InvalidOperationException(
                    "infrastructureScopeType must be null when meterType is not INFRASTRUCTURE");
        }
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
