package com.renterp.domain.tenancy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JoinRequestDecisionRequest {

    // Optional response note (T2).
    private String responseMessage;

    // For ACCEPT: startedAtBs (BS move-in) is required to open the membership.
    // For REJECT/CANCEL: not used.
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$",
             message = "startedAtBs must be YYYY-MM-DD BS format")
    @Size(max = 20)
    private String startedAtBs;
}
