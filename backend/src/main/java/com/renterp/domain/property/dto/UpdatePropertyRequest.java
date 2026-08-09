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
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdatePropertyRequest {

    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @Size(max = 500, message = "Address must not exceed 500 characters")
    private String address;

    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;

    private ElectricityBillingMode electricityBillingMode;

    private WaterMode waterMode;

    private SplitRule defaultSplitRule;

    private NeaTariffMode neaTariffMode;

    private MidMonthDepartureRateMode midMonthDepartureRateMode;

    // BS months run 29-32 days — never assume 30 (spec §6.4, §18.2). Range is 1-32, not 1-28.
    @Min(value = 1, message = "Billing day must be between 1 and 32")
    @Max(value = 32, message = "Billing day must be between 1 and 32")
    private Short billingDay;

    @Min(value = 0, message = "Grace period days cannot be negative")
    @Max(value = 90, message = "Grace period days must not exceed 90")
    private Short gracePeriodDays;

    private PaymentModel paymentModelDefault;

    @Min(value = 0, message = "Vacancy notice period days cannot be negative")
    @Max(value = 365, message = "Vacancy notice period days must not exceed 365")
    private Short vacancyNoticePeriodDays;

    private Boolean moveOutDayChargeable;

    private Boolean commonUnitsChargedToTenants;

    private PenaltyType penaltyType;

    private PenaltyFrequency penaltyFrequency;

    @Min(value = 0, message = "Penalty grace days cannot be negative")
    @Max(value = 90, message = "Penalty grace days must not exceed 90")
    private Short penaltyGraceDays;

    @DecimalMin(value = "0.00", message = "Penalty cap amount cannot be negative")
    private BigDecimal penaltyCapAmount;

    private LateDepartureChargeType lateDepartureChargeType;

    @DecimalMin(value = "0.00", message = "Late departure charge amount cannot be negative")
    private BigDecimal lateDepartureChargeAmount;

    private RoundingMethod roundingMethod;

    private RoundingRemainderTo roundingRemainderTo;

    @DecimalMin(value = "0.00", message = "Overage threshold percent cannot be negative")
    @DecimalMax(value = "100.00", message = "Overage threshold percent must not exceed 100")
    private BigDecimal overageThresholdPercent;

    private OverageAction overageAction;

    private Boolean tdsEnabled;

    @DecimalMin(value = "0.00", message = "TDS rate percent cannot be negative")
    @DecimalMax(value = "100.00", message = "TDS rate percent must not exceed 100")
    private BigDecimal tdsRatePercent;

    @Min(value = 0, message = "Dispute window days cannot be negative")
    @Max(value = 90, message = "Dispute window days must not exceed 90")
    private Short disputeWindowDays;
}
