package com.renterp.domain.tenancy.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** POST /api/v1/join/{code} — an optional note to the landlord. */
@Getter
@Setter
public class JoinByCodeRequest {

    @Size(max = 500, message = "message must be at most 500 characters")
    private String message;
}
