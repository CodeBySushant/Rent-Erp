package com.renterp.domain.moveout.dto;

import com.renterp.domain.moveout.entity.MoveOut;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A move-out and its money. Before settlement {@code preview*} shows what
 * settling today would do (outstanding bills, deposit held); after it the
 * recorded settlement fields are filled.
 */
public record MoveOutResponse(UUID id, UUID membershipId, UUID propertyId, String status, String noticeDateBs,
                              String plannedMoveOutBs, boolean shortNotice, boolean requestedByTenant, String reason,
                              String tenantName, String rooms,
                              BigDecimal previewOutstanding, BigDecimal previewDeposit,
                              String movedOutBs, BigDecimal outstandingBefore, BigDecimal finalCharges,
                              String finalChargesNote, BigDecimal deductions, String deductionsNote,
                              BigDecimal depositHeld, BigDecimal depositApplied, BigDecimal refundAmount,
                              BigDecimal tenantStillOwes) {

    public static MoveOutResponse of(MoveOut m, String tenantName, String rooms,
                                     BigDecimal previewOutstanding, BigDecimal previewDeposit) {
        return new MoveOutResponse(m.getId(), m.getMembershipId(), m.getPropertyId(), m.getStatus().name(),
                m.getNoticeDateBs(), m.getPlannedMoveOutBs(), m.isShortNotice(), m.isRequestedByTenant(),
                m.getReason(), tenantName, rooms, previewOutstanding, previewDeposit,
                m.getMovedOutBs(), m.getOutstandingBefore(), m.getFinalCharges(), m.getFinalChargesNote(),
                m.getDeductions(), m.getDeductionsNote(), m.getDepositHeld(), m.getDepositApplied(),
                m.getRefundAmount(), m.getTenantStillOwes());
    }
}
