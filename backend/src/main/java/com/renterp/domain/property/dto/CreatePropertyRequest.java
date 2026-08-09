package com.renterp.domain.property.dto;

import com.renterp.domain.property.entity.Property.ElectricityBillingMode;
import com.renterp.domain.property.entity.Property.LateDepartureChargeType;
import com.renterp.domain.property.entity.Property.MidMonthDepartureRateMode;
import com.renterp.domain.property.entity.Property.NeaTariffMode;
import com.renterp.domain.property.entity.Property.OverageAction;
import com.renterp.domain.property.entity.Property.PaymentModel;
import com.renterp.domain.property.entity.Property.PenaltyFrequency;
import com.renterp.domain.property.entity.Property.PenaltyType;
import com.renterp.domain.property.entity.Property.RoundingMethod;
import com.renterp.domain.property.entity.Property.RoundingRemainderTo;
import com.renterp.domain.property.entity.Property.SplitRule;
import com.renterp.domain.property.entity.Property.WaterMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class CreatePropertyRequest {

    // No auth context wired yet (Phase 1 only did OTP entities, not the JWT filter) —
    // owner is passed explicitly until an authenticated-principal resolver exists.
    @NotNull(message = "Owner user id is required")
    private UUID ownerUserId;

    @NotBlank(message = "Property name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @Size(max = 500, message = "Address must not exceed 500 characters")
    private String address;

    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;

    private ElectricityBillingMode electricityBillingMode = ElectricityBillingMode.SUB_METERED;

    private WaterMode waterMode = WaterMode.INCLUDED_IN_RENT;

    private SplitRule defaultSplitRule = SplitRule.EQUAL;

    private NeaTariffMode neaTariffMode = NeaTariffMode.FLAT_RATE;

    private MidMonthDepartureRateMode midMonthDepartureRateMode = MidMonthDepartureRateMode.PREVIOUS_MONTH;

    // BS months run 29-32 days — never assume 30 (spec §6.4, §18.2). Range is 1-32, not 1-28.
    @Min(value = 1, message = "Billing day must be between 1 and 32")
    @Max(value = 32, message = "Billing day must be between 1 and 32")
    private short billingDay = 1;

    @Min(value = 0, message = "Grace period days cannot be negative")
    @Max(value = 90, message = "Grace period days must not exceed 90")
    private short gracePeriodDays = 7;

    private PaymentModel paymentModelDefault = PaymentModel.PAY_IN_ADVANCE;

    @Min(value = 0, message = "Vacancy notice period days cannot be negative")
    @Max(value = 365, message = "Vacancy notice period days must not exceed 365")
    private short vacancyNoticePeriodDays = 30;

    private boolean moveOutDayChargeable = true;

    private boolean commonUnitsChargedToTenants = true;

    private PenaltyType penaltyType = PenaltyType.NONE;

    private PenaltyFrequency penaltyFrequency = PenaltyFrequency.PER_MONTH;

    @Min(value = 0, message = "Penalty grace days cannot be negative")
    @Max(value = 90, message = "Penalty grace days must not exceed 90")
    private short penaltyGraceDays = 5;

    // Null = no cap (spec default). Only meaningful when penaltyType != NONE.
    @DecimalMin(value = "0.00", message = "Penalty cap amount cannot be negative")
    private BigDecimal penaltyCapAmount;

    private LateDepartureChargeType lateDepartureChargeType = LateDepartureChargeType.NONE;

    // Fixed amount or percentage value depending on lateDepartureChargeType; null when type = NONE.
    @DecimalMin(value = "0.00", message = "Late departure charge amount cannot be negative")
    private BigDecimal lateDepartureChargeAmount;

    private RoundingMethod roundingMethod = RoundingMethod.WHOLE_NUMBER;

    private RoundingRemainderTo roundingRemainderTo = RoundingRemainderTo.HIGHEST_SHARE;

    @DecimalMin(value = "0.00", message = "Overage threshold percent cannot be negative")
    @DecimalMax(value = "100.00", message = "Overage threshold percent must not exceed 100")
    private BigDecimal overageThresholdPercent = new BigDecimal("3.00");

    private OverageAction overageAction = OverageAction.NOTIFY_AND_CONFIRM;

    private boolean tdsEnabled = false;

    // Null while tdsEnabled = false.
    @DecimalMin(value = "0.00", message = "TDS rate percent cannot be negative")
    @DecimalMax(value = "100.00", message = "TDS rate percent must not exceed 100")
    private BigDecimal tdsRatePercent;

    @Min(value = 0, message = "Dispute window days cannot be negative")
    @Max(value = 90, message = "Dispute window days must not exceed 90")
    private short disputeWindowDays = 7;
}
