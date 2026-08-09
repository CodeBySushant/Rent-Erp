package com.renterp.domain.charge.dto;

import com.renterp.domain.charge.entity.ChargeTemplate.SplitBasis;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdateChargeTemplateRequest {

    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @DecimalMin(value = "0.00", message = "Amount cannot be negative")
    private BigDecimal amount;

    private SplitBasis splitBasis;

    // Only meaningful when amount is being changed to 0.00 in this same request — see
    // ChargeTemplateService.updateChargeTemplate(). Null means "not provided", distinct
    // from false ("provided but not acknowledged").
    private Boolean zeroAmountAcknowledged;

    // propertyId is not updatable — moving a charge to a different property isn't a
    // plain field edit, same reasoning as ownerUserId/floorId elsewhere.
}
