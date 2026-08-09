package com.renterp.domain.meterreading.service;

import com.renterp.common.exception.InvalidOperationException;
import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.domain.meter.entity.Meter;
import com.renterp.domain.meter.entity.Meter.MeterStatus;
import com.renterp.domain.meter.repository.MeterRepository;
import com.renterp.domain.meterreading.dto.CorrectionRequest;
import com.renterp.domain.meterreading.dto.EstimationHintResponse;
import com.renterp.domain.meterreading.dto.MeterReadingResponse;
import com.renterp.domain.meterreading.dto.SubmitReadingRequest;
import com.renterp.domain.meterreading.entity.MeterReading;
import com.renterp.domain.meterreading.entity.MeterReading.ReadingStatus;
import com.renterp.domain.meterreading.entity.MeterReading.ReadingType;
import com.renterp.domain.meterreading.repository.MeterReadingRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class MeterReadingService {

    private static final Logger log = LogManager.getLogger(MeterReadingService.class);

    // Reading types the controller currently refuses to accept from a client — their
    // owning domains (tenants, vacancy, room assignments) aren't built yet. Internal
    // event flows (replacement, coverage) still write REPLACEMENT_*/COVERAGE_ANCHOR/
    // CORRECTION directly via package-visible helpers, bypassing this gate.
    private static final Set<ReadingType> DEFERRED_TYPES = EnumSet.of(
            ReadingType.VACANCY,
            ReadingType.TENANT_JOIN,
            ReadingType.DEPARTURE_TOPUP,
            ReadingType.GAP_ABSORBED
    );

    // Types that clients submit directly through POST /meters/{id}/readings. Everything
    // else is either deferred (above) or created only inside a wider event workflow.
    private static final Set<ReadingType> CLIENT_SUBMITTABLE = EnumSet.of(
            ReadingType.INITIAL,
            ReadingType.BILLING_RUN
    );

    // Types whose readings anchor a new chain and legitimately have no predecessor.
    // For every other type, a missing predecessor is a chain-integrity error.
    static final Set<ReadingType> CHAIN_ANCHOR_TYPES = EnumSet.of(
            ReadingType.INITIAL,
            ReadingType.REPLACEMENT_OPEN
    );

    private final MeterReadingRepository readingRepository;
    private final MeterRepository meterRepository;

    public MeterReadingService(MeterReadingRepository readingRepository, MeterRepository meterRepository) {
        this.readingRepository = readingRepository;
        this.meterRepository = meterRepository;
    }

    // ── Submit (creates PENDING) ────────────────────────────────────────────────

    @Transactional
    public MeterReadingResponse submitReading(UUID meterId, SubmitReadingRequest request) {
        log.debug("Submit reading — meter: {}, type: {}, value: {}, date: {}, rolloverConfirmed: {}",
                meterId, request.getReadingType(), request.getReadingValue(),
                request.getReadingDateBs(), request.isRolloverConfirmed());

        Meter meter = requireActiveMeter(meterId);
        gateReadingType(request.getReadingType());

        MeterReading pending = buildAndValidatePending(meter, request);
        MeterReading saved = readingRepository.save(pending);
        log.info("Reading submitted PENDING — id: {}, meter: {}, type: {}",
                saved.getId(), meterId, saved.getReadingType());
        return MeterReadingResponse.from(saved);
    }

    // ── Confirm (PENDING → CONFIRMED, stamps consumption) ───────────────────────

    @Transactional
    public MeterReadingResponse confirmReading(UUID readingId) {
        log.debug("Confirm reading — id: {}", readingId);

        MeterReading reading = requireReading(readingId);
        if (reading.getStatus() == ReadingStatus.CONFIRMED) {
            log.warn("Confirm skipped — already confirmed: {}", readingId);
            return MeterReadingResponse.from(reading);
        }
        Meter meter = requireActiveMeter(reading.getMeterId());

        stampConsumption(reading, meter);
        reading.setStatus(ReadingStatus.CONFIRMED);
        reading.setConfirmedAt(Instant.now());

        MeterReading saved = readingRepository.saveAndFlush(reading);
        log.info("Reading confirmed — id: {}, consumption: {}", saved.getId(), saved.getConsumption());
        return MeterReadingResponse.from(saved);
    }

    // ── Discard a draft (only allowed while PENDING) ───────────────────────────

    @Transactional
    public void discardPendingReading(UUID readingId) {
        MeterReading reading = requireReading(readingId);
        if (reading.getStatus() != ReadingStatus.PENDING) {
            throw new InvalidOperationException(
                    "Only PENDING readings may be discarded — this reading is CONFIRMED (spec §7.5: chain is immutable once confirmed)");
        }
        readingRepository.delete(reading);
        log.info("PENDING reading discarded — id: {}", readingId);
    }

    // ── Correction (new CORRECTION row, auto-CONFIRMED, links to original) ─────

    @Transactional
    public MeterReadingResponse correctReading(UUID readingId, CorrectionRequest request) {
        log.debug("Correcting reading — id: {}, newValue: {}", readingId, request.getCorrectedValue());

        MeterReading original = requireReading(readingId);
        if (original.getStatus() != ReadingStatus.CONFIRMED) {
            throw new InvalidOperationException(
                    "Only CONFIRMED readings may be corrected — PENDING draft should be edited by discard+resubmit");
        }
        if (original.getReadingType() == ReadingType.CORRECTION) {
            throw new InvalidOperationException(
                    "Cannot correct a CORRECTION — issue a new correction against the ORIGINAL reading instead");
        }
        Meter meter = requireActiveMeter(original.getMeterId());

        MeterReading correction = MeterReading.builder()
                .meterId(original.getMeterId())
                .propertyId(original.getPropertyId())
                .readingType(ReadingType.CORRECTION)
                .readingValue(request.getCorrectedValue())
                .readingDateBs(original.getReadingDateBs())
                .submissionDateBs(orDefault(request.getSubmissionDateBs(), original.getSubmissionDateBs()))
                .backdated(false)
                .status(ReadingStatus.CONFIRMED)   // auto-confirmed — corrections replace a mistake, not draft one
                .confirmedAt(Instant.now())
                .correctsReadingId(original.getId())
                .photoUrl(request.getPhotoUrl())
                .notes(request.getNotes())
                .build();

        // Corrections carry their own consumption calculated from the previous confirmed
        // reading BEFORE the original (skipping the original itself, which is being replaced
        // for accounting purposes even though it stays in the log for audit).
        stampConsumptionExcluding(correction, meter, original.getId());

        MeterReading saved = readingRepository.saveAndFlush(correction);
        log.info("Correction recorded — id: {}, corrects: {}, newValue: {}, consumption: {}",
                saved.getId(), original.getId(), saved.getReadingValue(), saved.getConsumption());
        return MeterReadingResponse.from(saved);
    }

    // ── Reads ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MeterReadingResponse getReadingById(UUID id) {
        return MeterReadingResponse.from(requireReading(id));
    }

    @Transactional(readOnly = true)
    public Page<MeterReadingResponse> getReadingsForMeter(UUID meterId, ReadingType type, ReadingStatus status, Pageable pageable) {
        if (!meterRepository.existsById(meterId)) {
            throw new ResourceNotFoundException("Meter", "id", meterId);
        }
        Page<MeterReading> page;
        if (type != null) {
            page = readingRepository.findByMeterIdAndReadingType(meterId, type, pageable);
        } else if (status != null) {
            page = readingRepository.findByMeterIdAndStatus(meterId, status, pageable);
        } else {
            page = readingRepository.findByMeterId(meterId, pageable);
        }
        return page.map(MeterReadingResponse::from);
    }

    // ── §14.1 M5 estimation hint (read-only) ───────────────────────────────────

    @Transactional(readOnly = true)
    public EstimationHintResponse estimationHint(UUID meterId) {
        if (!meterRepository.existsById(meterId)) {
            throw new ResourceNotFoundException("Meter", "id", meterId);
        }
        List<MeterReading> recent = readingRepository.findRecentConsumption(meterId, PageRequest.of(0, 3));
        if (recent.isEmpty()) {
            return EstimationHintResponse.builder()
                    .meterId(meterId).rollingAverage(null).sampleSize(0).samples(List.of())
                    .build();
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (MeterReading r : recent) sum = sum.add(r.getConsumption());
        BigDecimal avg = sum.divide(BigDecimal.valueOf(recent.size()), 2, RoundingMode.HALF_UP);

        return EstimationHintResponse.builder()
                .meterId(meterId)
                .rollingAverage(avg)
                .sampleSize(recent.size())
                .samples(recent.stream().map(r -> EstimationHintResponse.Sample.builder()
                        .readingId(r.getId())
                        .readingDateBs(r.getReadingDateBs())
                        .consumption(r.getConsumption())
                        .build()).toList())
                .build();
    }

    // ── Internal (package-visible) — used by replacement/coverage flows ───────

    /**
     * Insert a CONFIRMED reading directly. Used only inside event workflows (replacement,
     * coverage) — bypasses the {@link #DEFERRED_TYPES} gate and inserts already-confirmed.
     * The event workflow is the atomic unit; there's no draft state for event readings.
     */
    @Transactional
    public MeterReading insertConfirmedInternal(Meter meter, ReadingType type, BigDecimal value,
                                                 String readingDateBs, boolean estimated,
                                                 MeterReading.EstimationBasis estimationBasis,
                                                 String photoUrl, String notes,
                                                 UUID replacementEventId, UUID coverageEventId,
                                                 boolean skipConsumption) {

        MeterReading r = MeterReading.builder()
                .meterId(meter.getId())
                .propertyId(meter.getPropertyId())
                .readingType(type)
                .readingValue(value)
                .readingDateBs(readingDateBs)
                .submissionDateBs(readingDateBs)
                .backdated(false)
                .status(ReadingStatus.CONFIRMED)
                .confirmedAt(Instant.now())
                .estimated(estimated)
                .estimationBasis(estimationBasis)
                .photoUrl(photoUrl)
                .notes(notes)
                .replacementEventId(replacementEventId)
                .coverageEventId(coverageEventId)
                .build();

        if (!skipConsumption && !CHAIN_ANCHOR_TYPES.contains(type)) {
            stampConsumption(r, meter);
        }
        return readingRepository.saveAndFlush(r);
    }

    // ── Chain-integrity primitives ─────────────────────────────────────────────

    private void gateReadingType(ReadingType type) {
        if (DEFERRED_TYPES.contains(type)) {
            throw new InvalidOperationException(
                    type + " readings are deferred — owning domain not built yet (Phase 4/7)");
        }
        if (!CLIENT_SUBMITTABLE.contains(type)) {
            throw new InvalidOperationException(
                    type + " readings are only created by their event flow (replacement, coverage, correction), never directly");
        }
    }

    private MeterReading buildAndValidatePending(Meter meter, SubmitReadingRequest req) {
        Optional<MeterReading> prevOpt = readingRepository.findLatestConfirmed(meter.getId());
        BigDecimal value = req.getReadingValue();

        boolean isAnchor = CHAIN_ANCHOR_TYPES.contains(req.getReadingType());
        if (isAnchor && prevOpt.isPresent()) {
            throw new InvalidOperationException(
                    req.getReadingType() + " may only be submitted when no prior confirmed reading exists on this meter");
        }
        if (!isAnchor && prevOpt.isEmpty()) {
            throw new InvalidOperationException(
                    "No prior INITIAL reading exists on this meter — submit an INITIAL reading first");
        }

        // §14.1 M3 — zero readings blocked unless (a) it's a chain-anchor type or
        // (b) the meter genuinely rolled from max down to zero (rollover confirmed).
        if (value.compareTo(BigDecimal.ZERO) == 0 && !isAnchor && !req.isRolloverConfirmed()) {
            throw new InvalidOperationException(
                    "Reading value 0 is blocked unless this is an opening reading (INITIAL/REPLACEMENT_OPEN) or a confirmed rollover (§14.1 M3)");
        }

        boolean isRollover = false;
        if (!isAnchor && prevOpt.isPresent()) {
            BigDecimal prev = prevOpt.get().getReadingValue();
            // §14.1 M16 — backdate cannot predate the last confirmed reading's physical date.
            if (req.getReadingDateBs().compareTo(prevOpt.get().getReadingDateBs()) < 0) {
                throw new InvalidOperationException(
                        "readingDateBs cannot predate the last confirmed reading's date (§14.1 M16)");
            }
            // §14.1 M1/M2 — value below previous requires explicit rollover confirmation.
            if (value.compareTo(prev) < 0) {
                if (!req.isRolloverConfirmed()) {
                    throw new InvalidOperationException(
                        "readingValue " + value + " is less than the previous confirmed reading " + prev
                          + " — set rolloverConfirmed=true if the meter genuinely rolled over (§14.1 M1); otherwise this is an entry error (§14.1 M2)");
                }
                isRollover = true;
            }
        }

        // §14.1 M13 — atomic photo+reading applies to tenant path only (deferred until
        // tenants exist). Landlord submissions may omit photo per the same spec case.

        // §14.1 M16 — flag backdated when submission > reading date, regardless of value.
        String submissionDate = orDefault(req.getSubmissionDateBs(), req.getReadingDateBs());
        boolean backdated = submissionDate.compareTo(req.getReadingDateBs()) > 0;

        return MeterReading.builder()
                .meterId(meter.getId())
                .propertyId(meter.getPropertyId())
                .readingType(req.getReadingType())
                .readingValue(value)
                .readingDateBs(req.getReadingDateBs())
                .submissionDateBs(submissionDate)
                .backdated(backdated)
                .status(ReadingStatus.PENDING)
                .rollover(isRollover)
                .photoUrl(req.getPhotoUrl())
                .notes(req.getNotes())
                .build();
    }

    private void stampConsumption(MeterReading reading, Meter meter) {
        stampConsumptionExcluding(reading, meter, null);
    }

    private void stampConsumptionExcluding(MeterReading reading, Meter meter, UUID excludeId) {
        if (CHAIN_ANCHOR_TYPES.contains(reading.getReadingType())) {
            reading.setConsumption(null);
            return;
        }
        MeterReading prev = latestConfirmedExcluding(meter.getId(), excludeId);
        if (prev == null) {
            // Should not happen because buildAndValidatePending() enforces predecessor existence,
            // but corrections against the very first non-anchor reading would land here.
            reading.setConsumption(null);
            return;
        }
        BigDecimal consumption;
        BigDecimal cur = reading.getReadingValue();
        BigDecimal prv = prev.getReadingValue();
        if (reading.isRollover()) {
            // §14.1 M1 rollover math — (max − previous) + current
            consumption = meter.getMaxReadingValue().subtract(prv).add(cur);
        } else {
            consumption = cur.subtract(prv);
        }
        reading.setConsumption(consumption.setScale(2, RoundingMode.HALF_UP));
    }

    private MeterReading latestConfirmedExcluding(UUID meterId, UUID excludeId) {
        if (excludeId == null) {
            return readingRepository.findLatestConfirmed(meterId).orElse(null);
        }
        // Rare-path — walk a small window looking for the first confirmed reading
        // whose id != excludeId. Corrections against non-most-recent readings would
        // still find the correct predecessor here.
        List<MeterReading> hits = readingRepository.findLatestConfirmedForMeter(meterId, PageRequest.of(0, 5));
        for (MeterReading r : hits) {
            if (!r.getId().equals(excludeId)) return r;
        }
        return null;
    }

    private Meter requireActiveMeter(UUID meterId) {
        Meter m = meterRepository.findById(meterId)
                .orElseThrow(() -> new ResourceNotFoundException("Meter", "id", meterId));
        if (m.getStatus() == MeterStatus.INACTIVE) {
            throw new InvalidOperationException("Meter " + meterId + " is INACTIVE — no new readings accepted");
        }
        return m;
    }

    private MeterReading requireReading(UUID id) {
        return readingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MeterReading", "id", id));
    }

    private static String orDefault(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
