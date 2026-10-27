package com.renterp.common.exception;

import java.util.Locale;

/**
 * Turns internal entity names into what a client may see (M18).
 *
 * "TenantPropertyMembership" → code prefix TENANT_PROPERTY_MEMBERSHIP and the
 * words "Tenancy"; anything not listed falls back to splitting the camel case
 * ("MeterReading" → "Meter reading"). Ids and column names never reach the
 * message.
 */
final class ErrorNames {

    private ErrorNames() {
    }

    /** UPPER_SNAKE code prefix, e.g. MeterReading → METER_READING. */
    static String code(String resource) {
        if (resource == null || resource.isBlank()) {
            return "RESOURCE";
        }
        String friendly = switch (resource) {
            case "TenantPropertyMembership" -> "Tenancy";
            case "TenantProfile" -> "Tenant";
            case "TenantBill" -> "Bill";
            case "BillingRun" -> "BillingRun";
            default -> resource;
        };
        return friendly.replaceAll("([a-z0-9])([A-Z])", "$1_$2").replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    /** Readable words, e.g. MeterReading → "Meter reading". */
    static String words(String resource) {
        if (resource == null || resource.isBlank()) {
            return "This item";
        }
        return switch (resource) {
            case "TenantPropertyMembership" -> "Tenancy";
            case "TenantProfile" -> "Tenant";
            case "TenantBill" -> "Bill";
            case "TenantKyc" -> "Identity document";
            case "PropertyAccess" -> "Access grant";
            case "TariffVersion" -> "Tariff";
            default -> {
                String spaced = resource.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
                yield Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
            }
        };
    }
}
