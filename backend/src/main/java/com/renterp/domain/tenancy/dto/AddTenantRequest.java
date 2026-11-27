package com.renterp.domain.tenancy.dto;

import com.renterp.common.validation.BsDate;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership.PaymentModelOverride;
import com.renterp.domain.tenantfinance.entity.TenantOpeningBalance;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * POST /api/v1/properties/{propertyId}/tenants — the owner's "Add Tenant":
 * profile, membership, room assignments (with rent) and, optionally, the
 * deposit, advance rent and opening balance, created together or not at all.
 */
@Getter
@Setter
public class AddTenantRequest {

    @NotBlank(message = "fullName is required")
    @Size(min = 2, max = 255, message = "fullName must be 2 to 255 characters")
    private String fullName;

    @NotBlank(message = "phone is required")
    @Pattern(regexp = "^(97|98)\\d{8}$", message = "phone must be a valid 10-digit Nepal number starting with 97 or 98")
    private String phone;

    @Pattern(regexp = "^(97|98)\\d{8}$", message = "whatsappNumber must be a valid 10-digit Nepal number")
    private String whatsappNumber;

    @Pattern(regexp = "^(en|hi|ne)$", message = "preferredLanguage must be 'en', 'hi' or 'ne'")
    private String preferredLanguage = "en";

    @Min(1) @Max(50)
    private short numOccupants = 1;

    /**
     * Total Members: people living in this tenancy, 1 to 50. Older apps do not
     * send it; then {@link #numOccupants} (default 1) is used.
     */
    @Min(value = 1, message = "totalMembers must be at least 1")
    @Max(value = 50, message = "totalMembers can be at most 50")
    private Short totalMembers;

    @Size(max = 255)
    private String emergencyContactName;

    @Pattern(regexp = "^(97|98)\\d{8}$", message = "emergencyContactPhone must be a valid 10-digit Nepal number")
    private String emergencyContactPhone;

    /**
     * The tenant's rooms (1 to 10, no repeats). Older apps send one
     * {@link #roomId} instead; see {@link #chosenRoomIds()}.
     */
    @Size(max = 10, message = "A tenant can be given at most 10 rooms at once")
    private List<@NotNull(message = "roomIds cannot contain empty values") UUID> roomIds;

    /** One room — the older form of {@link #roomIds}. */
    private UUID roomId;

    @NotBlank(message = "moveInDateBs is required")
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$", message = "moveInDateBs must be YYYY-MM-DD (BS)")
    @BsDate
    private String moveInDateBs;

    @NotNull(message = "monthlyRent is required")
    @PositiveOrZero(message = "monthlyRent cannot be negative")
    @Digits(integer = 8, fraction = 2, message = "monthlyRent has too many digits")
    private BigDecimal monthlyRent;

    /**
     * Optional: the security deposit collected now. 0 or absent = nothing
     * collected yet (no deposit record is made).
     */
    @PositiveOrZero(message = "depositAmount cannot be negative")
    @Digits(integer = 8, fraction = 2, message = "depositAmount has too many digits")
    private BigDecimal depositAmount;

    /**
     * Optional: the security deposit agreed with the tenant. When absent and a
     * deposit is collected, the collected amount is taken as the agreed one.
     */
    @PositiveOrZero(message = "agreedDepositAmount cannot be negative")
    @Digits(integer = 8, fraction = 2, message = "agreedDepositAmount has too many digits")
    private BigDecimal agreedDepositAmount;

    /**
     * Optional: advance rent collected now. It is held and used against the
     * tenant's rent on the next bills. 0 or absent = none.
     */
    @PositiveOrZero(message = "advanceAmount cannot be negative")
    @Digits(integer = 8, fraction = 2, message = "advanceAmount has too many digits")
    private BigDecimal advanceAmount;

    /** Optional: the advance rent agreed; same rule as {@link #agreedDepositAmount}. */
    @PositiveOrZero(message = "agreedAdvanceAmount cannot be negative")
    @Digits(integer = 8, fraction = 2, message = "agreedAdvanceAmount has too many digits")
    private BigDecimal agreedAdvanceAmount;

    /**
     * Optional: money from before the tenant came on to Rentlo (it appears on
     * the first bill). 0 or absent = none; when more than 0,
     * {@link #openingBalanceDirection} says which way it goes.
     */
    @PositiveOrZero(message = "openingBalanceAmount cannot be negative")
    @Digits(integer = 8, fraction = 2, message = "openingBalanceAmount has too many digits")
    private BigDecimal openingBalanceAmount;

    private TenantOpeningBalance.Direction openingBalanceDirection;

    /** Defaults to the move-in date. */
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$", message = "depositReceivedAtBs must be YYYY-MM-DD (BS)")
    @BsDate
    private String depositReceivedAtBs;

    private PaymentModelOverride paymentModelOverride;

    /** {@link #roomIds} when sent, else the single {@link #roomId}, else empty. */
    public List<UUID> chosenRoomIds() {
        if (roomIds != null && !roomIds.isEmpty()) {
            return roomIds;
        }
        return roomId == null ? List.of() : List.of(roomId);
    }

    /** {@link #totalMembers} when sent, else {@link #numOccupants}. */
    public short chosenTotalMembers() {
        return totalMembers != null ? totalMembers : numOccupants;
    }

    /** {@code v} when more than 0, else null: 0 and absent both mean "none". */
    public static BigDecimal positiveOrNull(BigDecimal v) {
        return v != null && v.signum() > 0 ? v : null;
    }
}
