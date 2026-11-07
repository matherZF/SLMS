package com.wms.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StocktakeCountReq(@NotNull Long itemId, @NotNull @Min(0) Integer actualQty) {}
