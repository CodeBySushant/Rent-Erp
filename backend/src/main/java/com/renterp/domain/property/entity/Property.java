package com.renterp.domain.property.entity;

import com.renterp.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "properties")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Property extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "owner_user_id", nullable = false, columnDefinition = "uuid")
    private UUID ownerUserId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 500)
    private String address;

    @Column(length = 100)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private ElectricityBillingMode electricityBillingMode = ElectricityBillingMode.SUB_METERED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private WaterMode waterMode = WaterMode.INCLUDED_IN_RENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_split_rule", nullable = false, length = 50)
    @Builder.Default
    private SplitRule defaultSplitRule = SplitRule.EQUAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private NeaTariffMode neaTariffMode = NeaTariffMode.FLAT_RATE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private MidMonthDepartureRateMode midMonthDepartureRateMode = MidMonthDepartureRateMode.PREVIOUS_MONTH;

    // Billing day is 1-32, not 1-28 — BS months run 29-32 days and are never
    // assumed to be 30 (spec §6.4, §18.2). Range enforced in the request DTOs.
    @Column(name = "billing_day", nullable = false)
    @Builder.Default
    private short billingDay = 1;

    @Column(name = "grace_period_days", nullable = false)
    @Builder.Default
    private short gracePeriodDays = 7;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_model_default", nullable = false, length = 50)
    @Builder.Default
    private PaymentModel paymentModelDefault = PaymentModel.PAY_IN_ADVANCE;

    @Column(name = "vacancy_notice_period_days", nullable = false)
    @Builder.Default
    private short vacancyNoticePeriodDays = 30;

    @Column(name = "move_out_day_chargeable", nullable = false)
    @Builder.Default
    private boolean moveOutDayChargeable = true;

    @Column(name = "common_units_charged_to_tenants", nullable = false)
    @Builder.Default
    private boolean commonUnitsChargedToTenants = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "penalty_type", nullable = false, length = 50)
    @Builder.Default
    private PenaltyType penaltyType = PenaltyType.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "penalty_frequency", nullable = false, length = 50)
    @Builder.Default
    private PenaltyFrequency penaltyFrequency = PenaltyFrequency.PER_MONTH;

    @Column(name = "penalty_grace_days", nullable = false)
    @Builder.Default
    private short penaltyGraceDays = 5;

    // NULL = no cap (spec default). Only meaningful when penaltyType != NONE.
    @Column(name = "penalty_cap_amount", precision = 10, scale = 2)
    private BigDecimal penaltyCapAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "late_departure_charge_type", nullable = false, length = 50)
    @Builder.Default
    private LateDepartureChargeType lateDepartureChargeType = LateDepartureChargeType.NONE;

    // Fixed amount or percentage value depending on lateDepartureChargeType; NULL when type = NONE.
    @Column(name = "late_departure_charge_amount", precision = 10, scale = 2)
    private BigDecimal lateDepartureChargeAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "rounding_method", nullable = false, length = 50)
    @Builder.Default
    private RoundingMethod roundingMethod = RoundingMethod.WHOLE_NUMBER;

    @Enumerated(EnumType.STRING)
    @Column(name = "rounding_remainder_to", nullable = false, length = 50)
    @Builder.Default
    private RoundingRemainderTo roundingRemainderTo = RoundingRemainderTo.HIGHEST_SHARE;

    @Column(name = "overage_threshold_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal overageThresholdPercent = new BigDecimal("3.00");

    @Enumerated(EnumType.STRING)
    @Column(name = "overage_action", nullable = false, length = 50)
    @Builder.Default
    private OverageAction overageAction = OverageAction.NOTIFY_AND_CONFIRM;

    @Column(name = "tds_enabled", nullable = false)
    @Builder.Default
    private boolean tdsEnabled = false;

    // NULL while tdsEnabled = false.
    @Column(name = "tds_rate_percent", precision = 5, scale = 2)
    private BigDecimal tdsRatePercent;

    @Column(name = "dispute_window_days", nullable = false)
    @Builder.Default
    private short disputeWindowDays = 7;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    // createdAt / updatedAt are inherited from BaseAuditEntity and set by JPA auditing

    public enum ElectricityBillingMode {
        SUB_METERED, MAIN_METER_ONLY, FIXED_PER_TENANT, INCLUDED_IN_RENT
    }

    public enum WaterMode {
        INCLUDED_IN_RENT, FIXED_PER_TENANT, KUKL_SPLIT, BORING_PUMP_ONLY, KUKL_AND_BORING
    }

    public enum SplitRule {
        EQUAL, ROOM_WEIGHTED, CUSTOM
    }

    public enum NeaTariffMode {
        FLAT_RATE, BLENDED_RATE
    }

    public enum MidMonthDepartureRateMode {
        PREVIOUS_MONTH, DEFERRED
    }

    public enum PaymentModel {
        PAY_IN_ADVANCE, PAY_AFTER_STAY
    }

    public enum PenaltyType {
        NONE, FIXED, PERCENTAGE, PER_DAY
    }

    public enum PenaltyFrequency {
        ONE_TIME, PER_MONTH, PER_DAY
    }

    public enum LateDepartureChargeType {
        NONE, FIXED_PER_DAY, PERCENT_OF_DAILY_RENT, FLAT_FINE
    }

    public enum RoundingMethod {
        STANDARD, CEILING, FLOOR, WHOLE_NUMBER, EXACT
    }

    public enum RoundingRemainderTo {
        HIGHEST_SHARE, FIRST_TENANT, LAST_TENANT
    }

    public enum OverageAction {
        NOTIFY_ONLY, NOTIFY_AND_CONFIRM, BLOCK
    }
}
