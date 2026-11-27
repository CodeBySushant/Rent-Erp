package com.renterp.domain.charge.service;

import com.renterp.common.exception.ApiException;
import com.renterp.common.exception.InvalidOperationException;
import com.renterp.common.guard.PropertyGuard;
import com.renterp.domain.charge.dto.ChargeTemplateResponse;
import com.renterp.domain.charge.dto.CreateChargeTemplateRequest;
import com.renterp.domain.charge.dto.UpdateChargeTemplateRequest;
import com.renterp.domain.charge.entity.ChargeTemplate;
import com.renterp.domain.charge.entity.ChargeTemplate.DeactivationMode;
import com.renterp.domain.charge.entity.ChargeTemplate.SplitBasis;
import com.renterp.domain.charge.repository.ChargeTemplateRepository;
import com.renterp.domain.property.repository.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Charge Settings on the property page: add, re-add, edit (owner, 28 Nov). */
class ChargeTemplateServiceTest {

    private final UUID propertyId = UUID.randomUUID();
    private ChargeTemplateRepository repo;
    private ChargeTemplateService service;

    @BeforeEach
    void setUp() {
        repo = mock(ChargeTemplateRepository.class);
        PropertyRepository properties = mock(PropertyRepository.class);
        when(properties.existsById(propertyId)).thenReturn(true);
        service = new ChargeTemplateService(repo, properties, mock(PropertyGuard.class));
        when(repo.findByPropertyIdAndName(eq(propertyId), anyString())).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(repo.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }

    private CreateChargeTemplateRequest create(String name, String amount) {
        CreateChargeTemplateRequest r = new CreateChargeTemplateRequest();
        r.setPropertyId(propertyId);
        r.setName(name);
        r.setAmount(new BigDecimal(amount));
        r.setSplitBasis(SplitBasis.FIXED_PER_TENANT);
        r.setZeroAmountAcknowledged(new BigDecimal(amount).signum() == 0);
        return r;
    }

    private ChargeTemplate existing(String name, boolean active) {
        ChargeTemplate t = ChargeTemplate.builder()
                .id(UUID.randomUUID())
                .propertyId(propertyId)
                .name(name)
                .amount(new BigDecimal("500"))
                .splitBasis(SplitBasis.SHARED)
                .active(active)
                .build();
        if (!active) {
            t.setDeactivationMode(DeactivationMode.NEXT_CYCLE);
            t.setDeactivatedAtBs("2082-07-15");
        }
        when(repo.findByPropertyIdAndName(propertyId, name)).thenReturn(Optional.of(t));
        when(repo.existsByPropertyIdAndName(propertyId, name)).thenReturn(true);
        when(repo.findById(t.getId())).thenReturn(Optional.of(t));
        return t;
    }

    @Test
    void newChargeIsSavedWithATrimmedName() {
        ChargeTemplateResponse r = service.createChargeTemplate(create("  Internet ", "1000"));
        assertEquals("Internet", r.getName());
        assertEquals(new BigDecimal("1000"), r.getAmount());
    }

    @Test
    void zeroAmountIsAllowedWhenAcknowledged() {
        ChargeTemplateResponse r = service.createChargeTemplate(create("Garbage", "0"));
        assertEquals(0, r.getAmount().signum());
    }

    @Test
    void blankNameIsRefused() {
        InvalidOperationException e = assertThrows(InvalidOperationException.class,
                () -> service.createChargeTemplate(create("   ", "100")));
        assertEquals("CHARGE_NAME_REQUIRED", e.getCode());
    }

    @Test
    void sameNameAsAnActiveChargeIsRefusedWithAClearMessage() {
        existing("Internet", true);
        ApiException e = assertThrows(ApiException.class,
                () -> service.createChargeTemplate(create("Internet", "1200")));
        assertEquals("CHARGE_NAME_TAKEN", e.getCode());
        assertTrue(e.getMessage().contains("Internet"));
        verify(repo, never()).save(any());
    }

    @Test
    void addingARemovedChargeAgainBringsItBack() {
        ChargeTemplate old = existing("Internet", false);

        ChargeTemplateResponse r = service.createChargeTemplate(create("Internet", "1200"));

        assertEquals(old.getId(), r.getId());
        assertTrue(old.isActive());
        assertEquals(new BigDecimal("1200"), old.getAmount());
        assertEquals(SplitBasis.FIXED_PER_TENANT, old.getSplitBasis());
        assertNull(old.getDeactivationMode());
        assertNull(old.getDeactivatedAtBs());
        verify(repo, never()).save(any());
    }

    @Test
    void aRemovedChargeCannotBeEdited() {
        ChargeTemplate old = existing("Internet", false);
        UpdateChargeTemplateRequest u = new UpdateChargeTemplateRequest();
        u.setAmount(new BigDecimal("900"));
        InvalidOperationException e = assertThrows(InvalidOperationException.class,
                () -> service.updateChargeTemplate(old.getId(), u));
        assertEquals("CHARGE_REMOVED", e.getCode());
    }

    @Test
    void renameToATakenNameIsRefused() {
        ChargeTemplate water = existing("Water", true);
        existing("Internet", true);
        UpdateChargeTemplateRequest u = new UpdateChargeTemplateRequest();
        u.setName(" Internet ");
        ApiException e = assertThrows(ApiException.class, () -> service.updateChargeTemplate(water.getId(), u));
        assertEquals("CHARGE_NAME_TAKEN", e.getCode());
        assertEquals("Water", water.getName());
    }

    @Test
    void editChangesNameAmountAndType() {
        ChargeTemplate water = existing("Water", true);
        UpdateChargeTemplateRequest u = new UpdateChargeTemplateRequest();
        u.setName("Water (tanker)");
        u.setAmount(new BigDecimal("750"));
        u.setSplitBasis(SplitBasis.FIXED_PER_TENANT);

        service.updateChargeTemplate(water.getId(), u);

        assertEquals("Water (tanker)", water.getName());
        assertEquals(new BigDecimal("750"), water.getAmount());
        assertEquals(SplitBasis.FIXED_PER_TENANT, water.getSplitBasis());
    }

    @Test
    void blankRenameIsRefused() {
        ChargeTemplate water = existing("Water", true);
        UpdateChargeTemplateRequest u = new UpdateChargeTemplateRequest();
        u.setName("  ");
        InvalidOperationException e = assertThrows(InvalidOperationException.class,
                () -> service.updateChargeTemplate(water.getId(), u));
        assertEquals("CHARGE_NAME_REQUIRED", e.getCode());
    }

    @Test
    void removeRecordsHowItStops() {
        ChargeTemplate water = existing("Water", true);
        service.deactivateChargeTemplate(water.getId(), DeactivationMode.THIS_CYCLE_PRORATED, "2082-08-10");
        assertEquals(false, water.isActive());
        assertEquals(DeactivationMode.THIS_CYCLE_PRORATED, water.getDeactivationMode());
        assertEquals("2082-08-10", water.getDeactivatedAtBs());
    }
}
