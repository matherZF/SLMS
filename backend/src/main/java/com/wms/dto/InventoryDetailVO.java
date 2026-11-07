package com.wms.dto;

import java.time.Instant;
import java.time.LocalDate;

public record InventoryDetailVO(
        Long id,
        Long productId,
        String sku,
        String productName,
        Long locationId,
        String locationCode,
        String area,
        Integer qty,
        Integer reservedQty,
        Integer availableQty,
        Instant updatedAt,
        // 批次信息
        Long batchId,
        String itemKey,
        LocalDate productionDate,
        Integer shelfLifeDays,
        String manufacturer,
        LocalDate expiryDate
) {
}
