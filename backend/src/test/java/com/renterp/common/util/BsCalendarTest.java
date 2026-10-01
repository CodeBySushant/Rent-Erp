package com.renterp.common.util;

import com.renterp.common.exception.InvalidOperationException;
import com.renterp.common.util.BsCalendar.BsDate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the embedded BS month-length table (Open Verification Item #1: "confirm a
 * maintained library covers 2082+"). Pure unit test — no Spring context, no DB.
 *
 * <p>Expected values are the medic/bikram-sambat dataset for the years the billing engine
 * operates in. If a future edit to BsCalendar's table drifts from the authoritative data,
 * these assertions fail loudly.
 */
class BsCalendarTest {

    // BS 2082 month lengths per the authoritative dataset:
    // [31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30]
    @Test
    void daysInMonth_2082_matchesAuthoritativeData() {
        int[] expected = {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30};
        for (int m = 1; m <= 12; m++) {
            assertEquals(expected[m - 1], BsCalendar.daysInMonth(2082, m),
                    "days in BS 2082-" + m);
        }
    }

    @Test
    void everySupportedYearTotals365or366() {
        for (int y = BsCalendar.MIN_YEAR; y <= BsCalendar.MAX_YEAR; y++) {
            int total = 0;
            for (int m = 1; m <= 12; m++) total += BsCalendar.daysInMonth(y, m);
            assertTrue(total == 365 || total == 366,
                    "BS " + y + " total days = " + total + " (expected 365 or 366)");
        }
    }

    @Test
    void coversLaunchOperatingRange() {
        assertTrue(BsCalendar.MAX_YEAR >= 2085, "table must cover well past 2082");
        assertEquals(32, BsCalendar.daysInMonth(2082, 3));  // a 32-day month
        assertEquals(29, BsCalendar.daysInMonth(2082, 8));  // a 29-day month
    }

    @Test
    void parse_validAndInvalid() {
        BsDate d = BsCalendar.parse("2082-03-15");
        assertEquals(2082, d.year());
        assertEquals(3, d.month());
        assertEquals(15, d.day());

        // 2082-08 has only 29 days — day 30 is invalid
        assertThrows(InvalidOperationException.class, () -> BsCalendar.parse("2082-08-30"));
        assertThrows(InvalidOperationException.class, () -> BsCalendar.parse("2082-13-01"));
        assertThrows(InvalidOperationException.class, () -> BsCalendar.parse("bad-date"));
        assertFalse(BsCalendar.isValid("2082-08-30"));
        assertTrue(BsCalendar.isValid("2082-03-32"));  // month 3 has 32 days
    }

    @Test
    void yearOutsideRange_failsLoudly() {
        // The whole point of B14: never silently assume 30 days for an unknown year.
        assertThrows(InvalidOperationException.class, () -> BsCalendar.daysInMonth(2100, 1));
    }

    @Test
    void inclusiveDays_wholeMonth() {
        // A full BS month occupied start to end = that month's length (both ends counted).
        assertEquals(31, BsCalendar.inclusiveDays("2082-01-01", "2082-01-31"));  // 31-day month
        assertEquals(32, BsCalendar.inclusiveDays("2082-03-01", "2082-03-32"));  // 32-day month
        assertEquals(29, BsCalendar.inclusiveDays("2082-08-01", "2082-08-29"));  // 29-day month
    }

    @Test
    void inclusiveDays_midMonthSpan() {
        // Tenant occupies the 15th through the 20th inclusive = 6 days.
        assertEquals(6, BsCalendar.inclusiveDays("2082-03-15", "2082-03-20"));
    }

    @Test
    void daysBetween_acrossMonthAndYearBoundaries() {
        // 2082-01 has 31 days, so 01-01 -> 02-01 is exactly 31 days apart.
        assertEquals(31, BsCalendar.daysBetween("2082-01-01", "2082-02-01"));
        // 2081-12 has 31 days, so 2081-12-01 -> 2082-01-01 is 31 days apart.
        assertEquals(31, BsCalendar.daysBetween("2081-12-01", "2082-01-01"));
        // Symmetry: reverse direction is negative.
        assertEquals(-31, BsCalendar.daysBetween("2082-02-01", "2082-01-01"));
        // Same day = 0.
        assertEquals(0, BsCalendar.daysBetween("2082-03-15", "2082-03-15"));
    }

    @Test
    void inclusiveDays_rejectsReversedRange() {
        assertThrows(InvalidOperationException.class,
                () -> BsCalendar.inclusiveDays("2082-03-20", "2082-03-15"));
    }

    // ── AD → BS (feature/app-integration) ───────────────────────────────────

    @org.junit.jupiter.api.Test
    void fromAdMatchesKnownNewYears() {
        // Nepali New Year (Baishakh 1) fell on these AD dates.
        org.junit.jupiter.api.Assertions.assertEquals("2000-01-01",
                BsCalendar.fromAd(java.time.LocalDate.of(1943, 4, 14)));
        org.junit.jupiter.api.Assertions.assertEquals("2081-01-01",
                BsCalendar.fromAd(java.time.LocalDate.of(2024, 4, 13)));
        org.junit.jupiter.api.Assertions.assertEquals("2082-01-01",
                BsCalendar.fromAd(java.time.LocalDate.of(2025, 4, 14)));
    }

    @org.junit.jupiter.api.Test
    void monthStartAndTodayAreWellFormed() {
        org.junit.jupiter.api.Assertions.assertEquals("2082-06-01", BsCalendar.monthStart("2082-06-17"));
        org.junit.jupiter.api.Assertions.assertTrue(BsCalendar.isValid(BsCalendar.today()));
    }
}
