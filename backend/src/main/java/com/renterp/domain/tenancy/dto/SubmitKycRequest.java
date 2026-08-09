package com.renterp.domain.tenancy.dto;

import com.renterp.domain.tenancy.entity.TenantKyc.IdType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmitKycRequest {

    @NotNull(message = "idType is required")
    private IdType idType;

    @NotBlank(message = "idNumber is required")
    @Size(max = 100)
    private String idNumber;

    @Size(max = 500)
    private String photoFrontUrl;

    @Size(max = 500)
    private String photoBackUrl;

    @Size(max = 500)
    private String photoSelfieUrl;
}
