package com.renterp.domain.moveout.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MoveOutNoticeRequest {

    @NotBlank(message = "plannedMoveOutBs is required")
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$", message = "plannedMoveOutBs must be YYYY-MM-DD (BS)")
    private String plannedMoveOutBs;

    @Size(max = 500)
    private String reason;
}
