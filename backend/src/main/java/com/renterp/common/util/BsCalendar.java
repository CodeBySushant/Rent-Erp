package com.renterp.common.util;

import com.renterp.common.exception.InvalidOperationException;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Bikram Sambat (BS / Vikram Samvat) calendar utility.
 *
 * <p>Nepal's BS months are irregular — they run 29 to 32 days and never a fixed 30 — so
 * every proration in the billing engine (mid-month join credit T8, per-day penalties,
 * daily vacancy extensions) must divide by the <em>actual</em> length of the BS month,
 * never a hardcoded 30 (spec Edge Case B14 / §18.2). This class is that single source
 * of month-length truth.
 *
 * <h3>Why an embedded table instead of a Maven dependency</h3>
 * There is no maintained Java BS-calendar library on Maven Central. The candidates
 * (medic/bikram-sambat, nepali-bhasa/nepali-date-conversion, bahadurbaniya/...) publish
 * only to GitHub Packages or private staging repos, which would force auth tokens into
 * the build — a non-starter for a reproducible build. The month-length dataset itself is
 * small, finite and public, so we embed it directly and verify it (see BsCalendarTest).
 *
 * <p><b>Data source:</b> medic/bikram-sambat {@code test-data/daysInMonth.json} — a
 * widely-deployed, tested dataset used in production health systems. Covers BS 1970–2090.
 *
 * <p><b>Maintenance checkpoint:</b> the table ends at BS {@value #MAX_YEAR} (≈2033 AD).
 * Before that year approaches, extend the table from an updated authoritative source.
 * Any operation on a year outside [{@value #MIN_YEAR}, {@value #MAX_YEAR}] fails loudly
 * rather than silently assuming 30 days.
 *
 * <p>All arithmetic here is <em>pure BS</em> — no AD conversion is involved. Proration only
 * needs day counts and day differences within the BS calendar, both derivable from the
 * month-length table alone. BS↔AD conversion is a separate concern (display / scheduling)
 * and is deliberately not implemented here yet.
 */
public final class BsCalendar {

    private BsCalendar() {}

    public static final int MIN_YEAR = 1970;
    public static final int MAX_YEAR = 2090;

    private static final Pattern BS_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    // year -> 12 month lengths (index 0 = month 1 / Baishakh). Source: medic/bikram-sambat.
    private static final Map<Integer, int[]> M = new HashMap<>();
    static {
        M.put(1970, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(1971, new int[]{31, 31, 32, 31, 32, 30, 30, 29, 30, 29, 30, 30});
        M.put(1972, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31});
        M.put(1973, new int[]{30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31});
        M.put(1974, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(1975, new int[]{31, 31, 32, 32, 30, 31, 30, 29, 30, 29, 30, 30});
        M.put(1976, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31});
        M.put(1977, new int[]{30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31});
        M.put(1978, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(1979, new int[]{31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30});
        M.put(1980, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31});
        M.put(1981, new int[]{31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30});
        M.put(1982, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(1983, new int[]{31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30});
        M.put(1984, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31});
        M.put(1985, new int[]{31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30});
        M.put(1986, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(1987, new int[]{31, 32, 31, 32, 31, 30, 30, 29, 30, 29, 30, 30});
        M.put(1988, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31});
        M.put(1989, new int[]{31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(1990, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(1991, new int[]{31, 32, 31, 32, 31, 30, 30, 29, 30, 29, 30, 30});
        M.put(1992, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31});
        M.put(1993, new int[]{31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(1994, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(1995, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30});
        M.put(1996, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31});
        M.put(1997, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(1998, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(1999, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31});
        M.put(2000, new int[]{30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31});
        M.put(2001, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2002, new int[]{31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30});
        M.put(2003, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31});
        M.put(2004, new int[]{30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31});
        M.put(2005, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2006, new int[]{31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30});
        M.put(2007, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31});
        M.put(2008, new int[]{31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 29, 31});
        M.put(2009, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2010, new int[]{31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30});
        M.put(2011, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31});
        M.put(2012, new int[]{31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30});
        M.put(2013, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2014, new int[]{31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30});
        M.put(2015, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31});
        M.put(2016, new int[]{31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30});
        M.put(2017, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2018, new int[]{31, 32, 31, 32, 31, 30, 30, 29, 30, 29, 30, 30});
        M.put(2019, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31});
        M.put(2020, new int[]{31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2021, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2022, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30});
        M.put(2023, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31});
        M.put(2024, new int[]{31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2025, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2026, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31});
        M.put(2027, new int[]{30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31});
        M.put(2028, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2029, new int[]{31, 31, 32, 31, 32, 30, 30, 29, 30, 29, 30, 30});
        M.put(2030, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31});
        M.put(2031, new int[]{30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31});
        M.put(2032, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2033, new int[]{31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30});
        M.put(2034, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31});
        M.put(2035, new int[]{30, 32, 31, 32, 31, 31, 29, 30, 30, 29, 29, 31});
        M.put(2036, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2037, new int[]{31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30});
        M.put(2038, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31});
        M.put(2039, new int[]{31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30});
        M.put(2040, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2041, new int[]{31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30});
        M.put(2042, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31});
        M.put(2043, new int[]{31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30});
        M.put(2044, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2045, new int[]{31, 32, 31, 32, 31, 30, 30, 29, 30, 29, 30, 30});
        M.put(2046, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31});
        M.put(2047, new int[]{31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2048, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2049, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30});
        M.put(2050, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31});
        M.put(2051, new int[]{31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2052, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2053, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30});
        M.put(2054, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31});
        M.put(2055, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2056, new int[]{31, 31, 32, 31, 32, 30, 30, 29, 30, 29, 30, 30});
        M.put(2057, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31});
        M.put(2058, new int[]{30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31});
        M.put(2059, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2060, new int[]{31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30});
        M.put(2061, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31});
        M.put(2062, new int[]{30, 32, 31, 32, 31, 31, 29, 30, 29, 30, 29, 31});
        M.put(2063, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2064, new int[]{31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30});
        M.put(2065, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31});
        M.put(2066, new int[]{31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 29, 31});
        M.put(2067, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2068, new int[]{31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30});
        M.put(2069, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31});
        M.put(2070, new int[]{31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30});
        M.put(2071, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2072, new int[]{31, 32, 31, 32, 31, 30, 30, 29, 30, 29, 30, 30});
        M.put(2073, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31});
        M.put(2074, new int[]{31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2075, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2076, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30});
        M.put(2077, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31});
        M.put(2078, new int[]{31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2079, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2080, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30});
        M.put(2081, new int[]{31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31});
        M.put(2082, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2083, new int[]{31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30});
        M.put(2084, new int[]{31, 31, 32, 31, 31, 30, 30, 30, 29, 30, 30, 30});
        M.put(2085, new int[]{31, 32, 31, 32, 30, 31, 30, 30, 29, 30, 30, 30});
        M.put(2086, new int[]{30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 30, 30});
        M.put(2087, new int[]{31, 31, 32, 31, 31, 31, 30, 30, 29, 30, 30, 30});
        M.put(2088, new int[]{30, 31, 32, 32, 30, 31, 30, 30, 29, 30, 30, 30});
        M.put(2089, new int[]{30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 30, 30});
        M.put(2090, new int[]{30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 30, 30});
    }

    /** An immutable, validated BS calendar date. */
    public record BsDate(int year, int month, int day) {
        @Override public String toString() {
            return String.format("%04d-%02d-%02d", year, month, day);
        }
    }

    /** Number of days in the given BS month (month is 1-12). */
    public static int daysInMonth(int year, int month) {
        if (month < 1 || month > 12) {
            throw new InvalidOperationException("BS month out of range (1-12): " + month);
        }
        int[] months = M.get(year);
        if (months == null) {
            throw new InvalidOperationException(
                    "BS year " + year + " is outside the supported range "
                    + MIN_YEAR + "-" + MAX_YEAR + " — extend BsCalendar's table");
        }
        return months[month - 1];
    }

    /** Parse and fully validate a "YYYY-MM-DD" BS date string. */
    public static BsDate parse(String bs) {
        if (bs == null || !BS_DATE.matcher(bs).matches()) {
            throw new InvalidOperationException("Invalid BS date format (expected YYYY-MM-DD): " + bs);
        }
        int year = Integer.parseInt(bs.substring(0, 4));
        int month = Integer.parseInt(bs.substring(5, 7));
        int day = Integer.parseInt(bs.substring(8, 10));
        int max = daysInMonth(year, month);   // also validates year + month range
        if (day < 1 || day > max) {
            throw new InvalidOperationException(
                    "Invalid BS day " + day + " for " + year + "-" + month
                    + " (that month has " + max + " days)");
        }
        return new BsDate(year, month, day);
    }

    /** True if {@code bs} is a well-formed, in-range, real BS date. */
    public static boolean isValid(String bs) {
        try { parse(bs); return true; }
        catch (RuntimeException e) { return false; }
    }

    /**
     * Serial day number counting from {@value #MIN_YEAR}-01-01 = 0. Lets any two BS dates
     * be compared or subtracted without AD conversion.
     */
    private static long toEpochDay(BsDate d) {
        long days = 0;
        for (int y = MIN_YEAR; y < d.year(); y++) {
            for (int len : M.get(y)) days += len;
        }
        int[] months = M.get(d.year());
        for (int m = 1; m < d.month(); m++) days += months[m - 1];
        days += (d.day() - 1);
        return days;
    }

    /** Inverse of {@link #toEpochDay} — the BS date {@code epochDay} days after MIN_YEAR-01-01. */
    private static BsDate fromEpochDay(long epochDay) {
        if (epochDay < 0) {
            throw new InvalidOperationException("BS date underflow — before " + MIN_YEAR + "-01-01");
        }
        long remaining = epochDay;
        for (int y = MIN_YEAR; y <= MAX_YEAR; y++) {
            int[] months = M.get(y);
            for (int m = 1; m <= 12; m++) {
                int len = months[m - 1];
                if (remaining < len) {
                    return new BsDate(y, m, (int) remaining + 1);
                }
                remaining -= len;
            }
        }
        throw new InvalidOperationException(
                "BS date overflow — beyond " + MAX_YEAR + "-12; extend BsCalendar's table");
    }

    // ── AD → BS (added on feature/app-integration) ─────────────────────────────
    // Screens ask "what is overdue today?" and "which readings are due this
    // month?", which needs today's BS date. One fixed anchor is enough because
    // the month-length table above already encodes every BS month: BS
    // 2000-01-01 fell on AD 1943-04-14 (the same anchor the mobile app uses).

    private static final java.time.LocalDate AD_ANCHOR = java.time.LocalDate.of(1943, 4, 14);
    private static final String BS_ANCHOR = "2000-01-01";
    private static final java.time.ZoneId NEPAL = java.time.ZoneId.of("Asia/Kathmandu");

    /** The BS date of an AD date. */
    public static String fromAd(java.time.LocalDate ad) {
        long offset = java.time.temporal.ChronoUnit.DAYS.between(AD_ANCHOR, ad);
        return fromEpochDay(toEpochDay(parse(BS_ANCHOR)) + offset).toString();
    }

    /** Today's BS date in Nepal time (UTC+05:45), which is what a user means by "today". */
    public static String today() {
        return fromAd(java.time.LocalDate.now(NEPAL));
    }

    /** The first day of the BS month containing {@code bs}, e.g. 2082-06-17 → 2082-06-01. */
    public static String monthStart(String bs) {
        BsDate d = parse(bs);
        return new BsDate(d.year(), d.month(), 1).toString();
    }

    /**
     * The BS date {@code days} after {@code bs} (e.g. due date = generation date + grace
     * period). {@code days} may be negative. Result is validated in-range.
     */
    public static String addDays(String bs, int days) {
        return fromEpochDay(toEpochDay(parse(bs)) + days).toString();
    }

    /**
     * Signed day difference {@code to - from} (0 if same day, positive if {@code to} is
     * later). Both endpoints are validated.
     */
    public static long daysBetween(String fromBs, String toBs) {
        return toEpochDay(parse(toBs)) - toEpochDay(parse(fromBs));
    }

    /**
     * Inclusive day count of the span [fromBs, toBs] — how many days the tenant actually
     * occupied when both the move-in and move-out day are counted. Throws if {@code toBs}
     * precedes {@code fromBs}.
     */
    public static long inclusiveDays(String fromBs, String toBs) {
        long diff = daysBetween(fromBs, toBs);
        if (diff < 0) {
            throw new InvalidOperationException("toBs " + toBs + " precedes fromBs " + fromBs);
        }
        return diff + 1;
    }
}
