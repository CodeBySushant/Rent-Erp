package com.renterp.domain.tenancy.dto;

import com.renterp.domain.tenancy.entity.TenantKyc.IdType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class SubmitKycRequest {

    @NotNull(message = "idType is required")
    private IdType idType;

    @NotBlank(message = "idNumber is required")
    @Size(max = 100)
    private String idNumber;

    // H6: the photos are uploads (POST /api/v1/files) made by the tenant
    // themself, purposes KYC_ID_FRONT / KYC_ID_BACK / KYC_SELFIE. Free-text
    // URLs are no longer accepted.
    @NotNull(message = "idFrontFileId is required")
    private UUID idFrontFileId;

    private UUID idBackFileId;

    @NotNull(message = "selfieFileId is required")
    private UUID selfieFileId;
}
