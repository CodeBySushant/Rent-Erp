package com.renterp.domain.property.dto;

import com.renterp.domain.property.entity.Property;
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
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
public class PropertyResponse {

    private final UUID id;
    private final UUID ownerUserId;
    private final String name;
    private final String address;
    private final String city;
    private final ElectricityBillingMode electricityBillingMode;
    private final WaterMode waterMode;
    private final SplitRule defaultSplitRule;
    private final NeaTariffMode neaTariffMode;
    private final MidMonthDepartureRateMode midMonthDepartureRateMode;
    private final short billingDay;
    private final short gracePeriodDays;
    private final PaymentModel paymentModelDefault;
    private final short vacancyNoticePeriodDays;
    private final boolean moveOutDayChargeable;
    private final boolean commonUnitsChargedToTenants;
    private final PenaltyType penaltyType;
    private final PenaltyFrequency penaltyFrequency;
    private final short penaltyGraceDays;
    private final BigDecimal penaltyCapAmount;
    private final LateDepartureChargeType lateDepartureChargeType;
    private final BigDecimal lateDepartureChargeAmount;
    private final RoundingMethod roundingMethod;
    private final RoundingRemainderTo roundingRemainderTo;
    private final BigDecimal overageThresholdPercent;
    private final OverageAction overageAction;
    private final boolean tdsEnabled;
    private final BigDecimal tdsRatePercent;
    private final short disputeWindowDays;
    private final boolean active;
    private final String joinCode;
    private final Instant createdAt;
    private final Instant updatedAt;

    private PropertyResponse(Property property) {
        this.id = property.getId();
        this.ownerUserId = property.getOwnerUserId();
        this.name = property.getName();
        this.address = property.getAddress();
        this.city = property.getCity();
        this.electricityBillingMode = property.getElectricityBillingMode();
        this.waterMode = property.getWaterMode();
        this.defaultSplitRule = property.getDefaultSplitRule();
        this.neaTariffMode = property.getNeaTariffMode();
        this.midMonthDepartureRateMode = property.getMidMonthDepartureRateMode();
        this.billingDay = property.getBillingDay();
        this.gracePeriodDays = property.getGracePeriodDays();
        this.paymentModelDefault = property.getPaymentModelDefault();
        this.vacancyNoticePeriodDays = property.getVacancyNoticePeriodDays();
        this.moveOutDayChargeable = property.isMoveOutDayChargeable();
        this.commonUnitsChargedToTenants = property.isCommonUnitsChargedToTenants();
        this.penaltyType = property.getPenaltyType();
        this.penaltyFrequency = property.getPenaltyFrequency();
        this.penaltyGraceDays = property.getPenaltyGraceDays();
        this.penaltyCapAmount = property.getPenaltyCapAmount();
        this.lateDepartureChargeType = property.getLateDepartureChargeType();
        this.lateDepartureChargeAmount = property.getLateDepartureChargeAmount();
        this.roundingMethod = property.getRoundingMethod();
        this.roundingRemainderTo = property.getRoundingRemainderTo();
        this.overageThresholdPercent = property.getOverageThresholdPercent();
        this.overageAction = property.getOverageAction();
        this.tdsEnabled = property.isTdsEnabled();
        this.tdsRatePercent = property.getTdsRatePercent();
        this.disputeWindowDays = property.getDisputeWindowDays();
        this.active = property.isActive();
        this.joinCode = property.getJoinCode();
        this.createdAt = property.getCreatedAt();
        this.updatedAt = property.getUpdatedAt();
    }

    public static PropertyResponse from(Property property) {
        return new PropertyResponse(property);
    }
}
