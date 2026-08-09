package com.renterp.domain.charge.dto;

import com.renterp.domain.charge.entity.ChargeTemplate;
import com.renterp.domain.charge.entity.ChargeTemplate.DeactivationMode;
import com.renterp.domain.charge.entity.ChargeTemplate.SplitBasis;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
public class ChargeTemplateResponse {

    private final UUID id;
    private final UUID propertyId;
    private final String name;
    private final BigDecimal amount;
    private final SplitBasis splitBasis;
    private final boolean zeroAmountAcknowledged;
    private final DeactivationMode deactivationMode;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private ChargeTemplateResponse(ChargeTemplate template) {
        this.id = template.getId();
        this.propertyId = template.getPropertyId();
        this.name = template.getName();
        this.amount = template.getAmount();
        this.splitBasis = template.getSplitBasis();
        this.zeroAmountAcknowledged = template.isZeroAmountAcknowledged();
        this.deactivationMode = template.getDeactivationMode();
        this.active = template.isActive();
        this.createdAt = template.getCreatedAt();
        this.updatedAt = template.getUpdatedAt();
    }

    public static ChargeTemplateResponse from(ChargeTemplate template) {
        return new ChargeTemplateResponse(template);
    }
}
