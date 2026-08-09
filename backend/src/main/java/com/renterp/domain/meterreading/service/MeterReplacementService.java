package com.renterp.domain.meterreading.service;

import com.renterp.common.exception.InvalidOperationException;
import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.domain.meter.entity.Meter;
import com.renterp.domain.meter.entity.Meter.MeterStatus;
import com.renterp.domain.meter.entity.MeterRoomCoverage;
import com.renterp.domain.meter.repository.MeterRepository;
import com.renterp.domain.meter.repository.MeterRoomCoverageRepository;
import com.renterp.domain.meterreading.dto.CreateReplacementRequest;
import com.renterp.domain.meterreading.dto.CreateReplacementRequest.ReadingSpec;
import com.renterp.domain.meterreading.dto.ReplacementEventResponse;
import com.renterp.domain.meterreading.entity.MeterReading;
import com.renterp.domain.meterreading.entity.MeterReading.EstimationBasis;
import com.renterp.domain.meterreading.entity.MeterReading.ReadingType;
import com.renterp.domain.meterreading.entity.MeterReplacementEvent;
import com.renterp.domain.meterreading.repository.MeterReplacementEventRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class MeterReplacementService {

    private static final Logger log = LogManager.getLogger(MeterReplacementService.class);

    private final MeterRepository meterRepository;
    private final MeterRoomCoverageRepository coverageRepository;
    private final MeterReplacementEventRepository replacementEventRepository;
    private final MeterReadingService readingService;

    public MeterReplacementService(MeterRepository meterRepository,
                                    MeterRoomCoverageRepository coverageRepository,
                                    MeterReplacementEventRepository replacementEventRepository,
                                    MeterReadingService readingService) {
        this.meterRepository = meterRepository;
        this.coverageRepository = coverageRepository;
        this.replacementEventRepository = replacementEventRepository;
        this.readingService = readingService;
    }

    @Transactional
    public ReplacementEventResponse replace(UUID oldMeterId, CreateReplacementRequest req) {
        log.debug("Replacing meter — old: {}, date: {}, reason: {}",
                oldMeterId, req.getReplacementDateBs(), req.getReason());

        Meter oldMeter = meterRepository.findById(oldMeterId)
                .orElseThrow(() -> new ResourceNotFoundException("Meter", "id", oldMeterId));
        if (oldMeter.getStatus() == MeterStatus.INACTIVE) {
            throw new InvalidOperationException("Cannot replace an INACTIVE meter — it has already been closed");
        }
        if (oldMeter.getReplacedByMeterId() != null) {
            throw new InvalidOperationException("Meter has already been replaced (replacement chain: " + oldMeter.getReplacedByMeterId() + ")");
        }

        validateEstimation(req.getCloseReading());
        validateEstimation(req.getOpenReading());

        // 1. Create the successor meter carrying every characteristic forward except
        // identity (label, serial, id, status, replacement pointer). Spec §7.8:
        // "coverage/assignment/split carry forward".
        Meter newMeter = Meter.builder()
                .propertyId(oldMeter.getPropertyId())
                .serialNumber(nullIfBlank(req.getNewMeterSerialNumber()))
                .label(req.getNewMeterLabel())
                .meterPurpose(oldMeter.getMeterPurpose())
                .meterType(oldMeter.getMeterType())
                .infrastructureScopeType(oldMeter.getInfrastructureScopeType())
                .splitRuleOverride(oldMeter.getSplitRuleOverride())
                .readingResponsibility(oldMeter.getReadingResponsibility())
                .designatedTenantId(oldMeter.getDesignatedTenantId())
                .maxReadingValue(oldMeter.getMaxReadingValue())
                .status(MeterStatus.ACTIVE)
                .build();
        newMeter = meterRepository.saveAndFlush(newMeter);
        log.debug("New meter created — id: {}", newMeter.getId());

        // 2. Carry coverage forward. For every active coverage row on the old meter,
        // end it on eventDateBs and open a matching row on the new meter starting the
        // same day. Historical bills read old-meter coverage as-of-past dates; new
        // bills read new-meter coverage as-of-now (spec §14.1 M4/M9).
        for (MeterRoomCoverage cov : coverageRepository.findByMeterIdOrderByEffectiveFromBsAsc(oldMeter.getId())) {
            if (cov.getEffectiveToBs() == null) {
                cov.setEffectiveToBs(req.getReplacementDateBs());
                coverageRepository.saveAndFlush(cov);

                MeterRoomCoverage carried = MeterRoomCoverage.builder()
                        .meterId(newMeter.getId())
                        .roomId(cov.getRoomId())
                        .effectiveFromBs(req.getReplacementDateBs())
                        .build();
                coverageRepository.save(carried);
            }
        }

        // 3. Insert REPLACEMENT_CLOSE on old meter (consumption stamped from previous
        // confirmed reading in the OLD chain — normal delta math).
        MeterReading closeReading = readingService.insertConfirmedInternal(
                oldMeter,
                ReadingType.REPLACEMENT_CLOSE,
                req.getCloseReading().getReadingValue(),
                req.getReplacementDateBs(),
                req.getCloseReading().isEstimated(),
                req.getCloseReading().getEstimationBasis(),
                req.getCloseReading().getPhotoUrl(),
                req.getCloseReading().getNotes(),
                /* replacementEventId */ null,       // filled in on the event row below
                /* coverageEventId */ null,
                /* skipConsumption */ false
        );

        // 4. Insert REPLACEMENT_OPEN on new meter (chain anchor, no consumption).
        MeterReading openReading = readingService.insertConfirmedInternal(
                newMeter,
                ReadingType.REPLACEMENT_OPEN,
                req.getOpenReading().getReadingValue(),
                req.getReplacementDateBs(),
                req.getOpenReading().isEstimated(),
                req.getOpenReading().getEstimationBasis(),
                req.getOpenReading().getPhotoUrl(),
                req.getOpenReading().getNotes(),
                null,
                null,
                /* skipConsumption */ true
        );

        // 5. Old meter — mark INACTIVE and set replaced_by pointer.
        oldMeter.setStatus(MeterStatus.INACTIVE);
        oldMeter.setActive(false);
        oldMeter.setReplacedByMeterId(newMeter.getId());
        meterRepository.saveAndFlush(oldMeter);

        // 6. Persist the event row and back-fill event id on both readings.
        MeterReplacementEvent event = MeterReplacementEvent.builder()
                .oldMeterId(oldMeter.getId())
                .newMeterId(newMeter.getId())
                .closeReadingId(closeReading.getId())
                .openReadingId(openReading.getId())
                .replacementDateBs(req.getReplacementDateBs())
                .reason(req.getReason())
                .build();
        event = replacementEventRepository.saveAndFlush(event);

        closeReading.setReplacementEventId(event.getId());
        openReading.setReplacementEventId(event.getId());
        // saveAndFlush both — no repo re-fetch needed, JPA is tracking these managed instances.
        log.info("Meter replaced — event: {}, old: {} → new: {}", event.getId(), oldMeter.getId(), newMeter.getId());

        return ReplacementEventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public List<ReplacementEventResponse> listForMeter(UUID meterId) {
        if (!meterRepository.existsById(meterId)) {
            throw new ResourceNotFoundException("Meter", "id", meterId);
        }
        return replacementEventRepository
                .findByOldMeterIdOrNewMeterIdOrderByCreatedAtDesc(meterId, meterId)
                .stream().map(ReplacementEventResponse::from).toList();
    }

    private void validateEstimation(ReadingSpec spec) {
        if (spec.isEstimated() && spec.getEstimationBasis() == null) {
            throw new InvalidOperationException(
                    "estimationBasis is required when estimated=true (spec §14.1 M5)");
        }
        if (!spec.isEstimated() && spec.getEstimationBasis() != null) {
            throw new InvalidOperationException(
                    "estimationBasis must be null when estimated=false");
        }
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
