package com.wms.dto;

import java.time.Instant;

public record InventoryAdjustmentVO(
        String refNo,
        String type,
        Long inventoryId,
        Integer beforeQty,
        Integer changeQty,
        Integer afterQty,
        String operator,
        Instant operatedAt
) {
}
