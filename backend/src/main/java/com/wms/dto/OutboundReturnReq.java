package com.wms.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OutboundReturnReq(
        @NotNull Long itemId,
        @NotNull @Min(1) Integer qty,
        @NotBlank String reason
) {
}
