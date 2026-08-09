package com.renterp.domain.billing.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Optional body for confirming a run — carries the confirming user id (FK when auth lands). */
@Getter
@Setter
public class ConfirmBillingRunRequest {
    private UUID confirmedBy;
}
