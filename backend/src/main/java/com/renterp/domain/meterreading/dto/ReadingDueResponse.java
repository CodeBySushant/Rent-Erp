package com.renterp.domain.meterreading.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One meter and where its reading for the current BS month stands.
 *
 * @param thisMonth   NONE (nothing yet), PENDING (submitted, waiting for the
 *                    owner) or CONFIRMED
 * @param canSubmit   for the tenant view: whether the caller may submit it
 * @param reason      why not, when {@code canSubmit} is false
 */
public record ReadingDueResponse(UUID meterId, UUID propertyId, String label, String serialNumber, String meterType,
                                 String meterPurpose, String responsibility, List<String> rooms,
                                 BigDecimal lastValue, String lastDateBs,
                                 String thisMonth, UUID thisMonthReadingId, BigDecimal thisMonthValue,
                                 String thisMonthPhotoUrl, boolean submittedByTenant,
                                 boolean canSubmit, String reason) {
}
