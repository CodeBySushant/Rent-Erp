package com.renterp.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** M18: specific codes and plain messages, without internal names or ids. */
class ErrorNamesTest {

    @Test
    void notFoundHasSpecificCodeAndPlainMessage() {
        ResourceNotFoundException e = new ResourceNotFoundException("MeterReading", "id", "abc");
        assertEquals("METER_READING_NOT_FOUND", e.getCode());
        assertEquals("Meter reading not found.", e.getMessage());
        assertFalse(e.getMessage().contains("abc"));
    }

    @Test
    void internalNamesAreReplaced() {
        ResourceNotFoundException e = new ResourceNotFoundException("TenantPropertyMembership", "id", "x");
        assertEquals("TENANCY_NOT_FOUND", e.getCode());
        assertEquals("Tenancy not found.", e.getMessage());
    }

    @Test
    void duplicateHasSpecificCode() {
        DuplicateResourceException e = new DuplicateResourceException("Room", "floorId+name", "x");
        assertEquals("ROOM_ALREADY_EXISTS", e.getCode());
        assertEquals("Room already exists.", e.getMessage());
    }

    @Test
    void invalidOperationKeepsLegacyCodeByDefault() {
        assertEquals("INVALID_OPERATION", new InvalidOperationException("x").getCode());
        assertEquals("PERIOD_OVERLAP", new InvalidOperationException("PERIOD_OVERLAP", "x").getCode());
    }
}
