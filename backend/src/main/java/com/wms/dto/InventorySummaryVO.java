package com.wms.dto;

public record InventorySummaryVO(
        Long productId,
        String sku,
        String name,
        String spec,
        String unit,
        Integer totalQty,
        Integer reservedQty,
        Integer availableQty
) {
}
