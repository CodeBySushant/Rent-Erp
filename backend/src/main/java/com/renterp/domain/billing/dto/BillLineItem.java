package com.renterp.domain.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One display line on a tenant bill, stored inside {@code tenant_bills.line_items} JSONB.
 * Display/audit snapshot only — never filtered on; the authoritative per-component amounts
 * live in the bill's typed columns.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillLineItem {

    private String label;        // "Rent", "Electricity (main meter share)", "Internet", ...
    private String type;         // RENT | ELECTRICITY | WATER | CHARGE | ADJUSTMENT | PREVIOUS_BALANCE | ADVANCE | TDS | ROUNDING
    private BigDecimal quantity; // e.g. prorated day fraction or unit count; null when not applicable
    private BigDecimal rate;     // per-unit / per-day rate; null when not applicable
    private BigDecimal amount;   // signed: positive = charge, negative = credit/deduction
    private String note;         // free-text explanation (e.g. "prorated 12/30 days", B2 date-range note)

    public static BillLineItem of(String label, String type, BigDecimal amount, String note) {
        return new BillLineItem(label, type, null, null, amount, note);
    }
}
