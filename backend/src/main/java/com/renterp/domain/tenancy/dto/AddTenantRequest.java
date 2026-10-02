package com.renterp.domain.tenancy.dto;

import com.renterp.domain.tenancy.entity.TenantPropertyMembership.PaymentModelOverride;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * POST /api/v1/properties/{propertyId}/tenants — the owner's "Add Tenant":
 * profile, membership, room assignment (with rent) and, optionally, the
 * deposit, created together or not at all.
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

    @Size(max = 255)
    private String emergencyContactName;

    @Pattern(regexp = "^(97|98)\\d{8}$", message = "emergencyContactPhone must be a valid 10-digit Nepal number")
    private String emergencyContactPhone;

    @NotNull(message = "roomId is required")
    private UUID roomId;

    @NotBlank(message = "moveInDateBs is required")
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$", message = "moveInDateBs must be YYYY-MM-DD (BS)")
    private String moveInDateBs;

    @NotNull(message = "monthlyRent is required")
    @PositiveOrZero(message = "monthlyRent cannot be negative")
    @Digits(integer = 8, fraction = 2, message = "monthlyRent has too many digits")
    private BigDecimal monthlyRent;

    /** Optional: the security deposit received at move-in. */
    @Positive(message = "depositAmount must be more than zero")
    @Digits(integer = 8, fraction = 2, message = "depositAmount has too many digits")
    private BigDecimal depositAmount;

    /** Defaults to the move-in date. */
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$", message = "depositReceivedAtBs must be YYYY-MM-DD (BS)")
    private String depositReceivedAtBs;

    private PaymentModelOverride paymentModelOverride;
}
