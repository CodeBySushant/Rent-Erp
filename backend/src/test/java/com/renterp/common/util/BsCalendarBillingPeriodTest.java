package com.renterp.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** M5: the billing period closing "now" follows the property's billing day. */
class BsCalendarBillingPeriodTest {

    @Test
    void beforeTheBillingDayItIsStillLastMonth() {
        assertEquals("2082-05-01", BsCalendar.billingPeriodStart("2082-06-03", 5));
    }

    @Test
    void onOrAfterTheBillingDayItIsThisMonth() {
        assertEquals("2082-06-01", BsCalendar.billingPeriodStart("2082-06-05", 5));
        assertEquals("2082-06-01", BsCalendar.billingPeriodStart("2082-06-20", 5));
    }

    @Test
    void billingDayOneIsAlwaysThisMonth() {
        assertEquals("2082-06-01", BsCalendar.billingPeriodStart("2082-06-01", 1));
    }

    @Test
    void januaryRollsBackToTheYearBefore() {
        assertEquals("2081-12-01", BsCalendar.billingPeriodStart("2082-01-02", 10));
    }

    @Test
    void monthEndIsTheLastDayOfTheBsMonth() {
        String end = BsCalendar.monthEnd("2082-06-17");
        assertEquals("2082-06-" + String.format("%02d", BsCalendar.daysInMonth(2082, 6)), end);
    }
}
