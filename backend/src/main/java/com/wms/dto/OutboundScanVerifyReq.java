package com.wms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OutboundScanVerifyReq(
        @NotNull Long itemId,
        @NotBlank String partCode,
        @NotBlank String locationCode) {
}
