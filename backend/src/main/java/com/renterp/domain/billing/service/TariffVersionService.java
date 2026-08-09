package com.renterp.domain.billing.service;

import com.renterp.common.exception.DuplicateResourceException;
import com.renterp.common.exception.InvalidOperationException;
import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.common.util.BsCalendar;
import com.renterp.domain.billing.dto.CreateTariffRequest;
import com.renterp.domain.billing.dto.TariffResponse;
import com.renterp.domain.billing.dto.UpdateTariffRequest;
import com.renterp.domain.billing.entity.BillingRun;
import com.renterp.domain.billing.entity.TariffVersion;
import com.renterp.domain.billing.repository.BillingRunRepository;
import com.renterp.domain.billing.repository.TariffVersionRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TariffVersionService {

    private static final Logger log = LogManager.getLogger(TariffVersionService.class);

    private final TariffVersionRepository tariffRepository;
    private final BillingRunRepository billingRunRepository;

    public TariffVersionService(TariffVersionRepository tariffRepository,
                                BillingRunRepository billingRunRepository) {
        this.tariffRepository = tariffRepository;
        this.billingRunRepository = billingRunRepository;
    }

    @Transactional
    public TariffResponse create(CreateTariffRequest req) {
        validateDates(req.getEffectiveFromBs(), req.getEffectiveToBs());
        if (tariffRepository.existsByEffectiveFromBsAndActiveTrue(req.getEffectiveFromBs())) {
            throw new DuplicateResourceException("Active TariffVersion", "effectiveFromBs", req.getEffectiveFromBs());
        }
        TariffVersion t = TariffVersion.builder()
                .name(req.getName())
                .effectiveFromBs(req.getEffectiveFromBs())
                .effectiveToBs(req.getEffectiveToBs())
                .slabs(req.getSlabs().stream().map(CreateTariffRequest.SlabInput::toSlab).toList())
                .demandCharge(req.getDemandCharge())
                .serviceCharge(req.getServiceCharge())
                .minimumCharge(req.getMinimumCharge())
                .vatPercent(req.getVatPercent())
                .notes(req.getNotes())
                .build();
        TariffVersion saved = tariffRepository.saveAndFlush(t);
        log.info("Tariff version created — id: {}, effectiveFrom: {}", saved.getId(), saved.getEffectiveFromBs());
        return TariffResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public TariffResponse getById(UUID id) {
        return TariffResponse.from(require(id));
    }

    @Transactional(readOnly = true)
    public Page<TariffResponse> list(Pageable pageable) {
        return tariffRepository.findByActiveTrue(pageable).map(TariffResponse::from);
    }

    /** The active tariff in force on a BS date (spec §9.2 step 1 for blended-rate billing). */
    @Transactional(readOnly = true)
    public TariffResponse effectiveOn(String asOfBs) {
        if (!BsCalendar.isValid(asOfBs)) {
            throw new InvalidOperationException("asOfBs is not a valid BS date: " + asOfBs);
        }
        List<TariffVersion> matches = tariffRepository.findEffectiveOn(asOfBs, PageRequest.of(0, 1));
        if (matches.isEmpty()) {
            throw new ResourceNotFoundException("TariffVersion", "effectiveOn", asOfBs);
        }
        return TariffResponse.from(matches.get(0));
    }

    @Transactional
    public TariffResponse update(UUID id, UpdateTariffRequest req) {
        TariffVersion t = require(id);
        // B5 — a schedule a confirmed bill was priced against is frozen.
        if (billingRunRepository.existsByTariffVersionIdAndStatus(id, BillingRun.Status.CONFIRMED)) {
            throw new InvalidOperationException(
                    "Tariff version " + id + " is referenced by a CONFIRMED billing run and cannot be edited");
        }
        validateDates(req.getEffectiveFromBs(), req.getEffectiveToBs());
        // If the start date is moving, keep the active-uniqueness rule intact.
        if (!t.getEffectiveFromBs().equals(req.getEffectiveFromBs())
                && tariffRepository.existsByEffectiveFromBsAndActiveTrue(req.getEffectiveFromBs())) {
            throw new DuplicateResourceException("Active TariffVersion", "effectiveFromBs", req.getEffectiveFromBs());
        }
        t.setName(req.getName());
        t.setEffectiveFromBs(req.getEffectiveFromBs());
        t.setEffectiveToBs(req.getEffectiveToBs());
        t.setSlabs(req.getSlabs().stream().map(CreateTariffRequest.SlabInput::toSlab).toList());
        t.setDemandCharge(req.getDemandCharge());
        t.setServiceCharge(req.getServiceCharge());
        t.setMinimumCharge(req.getMinimumCharge());
        t.setVatPercent(req.getVatPercent());
        t.setNotes(req.getNotes());
        TariffVersion saved = tariffRepository.saveAndFlush(t);
        log.info("Tariff version updated — id: {}", saved.getId());
        return TariffResponse.from(saved);
    }

    /** Soft-delete (deactivate). Blocked if a CONFIRMED run references it. */
    @Transactional
    public void delete(UUID id) {
        TariffVersion t = require(id);
        if (billingRunRepository.existsByTariffVersionIdAndStatus(id, BillingRun.Status.CONFIRMED)) {
            throw new InvalidOperationException(
                    "Tariff version " + id + " is referenced by a CONFIRMED billing run and cannot be deleted");
        }
        t.setActive(false);
        tariffRepository.saveAndFlush(t);
        log.info("Tariff version deactivated — id: {}", id);
    }

    private void validateDates(String fromBs, String toBs) {
        BsCalendar.parse(fromBs);   // throws InvalidOperationException (→400) if malformed/out-of-range
        if (toBs != null) {
            BsCalendar.parse(toBs);
            if (BsCalendar.daysBetween(fromBs, toBs) < 0) {
                throw new InvalidOperationException(
                        "effectiveToBs " + toBs + " precedes effectiveFromBs " + fromBs);
            }
        }
    }

    private TariffVersion require(UUID id) {
        return tariffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TariffVersion", "id", id));
    }
}
