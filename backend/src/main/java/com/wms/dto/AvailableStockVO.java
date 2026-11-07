package com.wms.dto;

import java.time.LocalDate;

/**
 * 可选库存（含批次详情），用于出库时人工选择库位/批次
 */
public record AvailableStockVO(
        Long inventoryId,
        Long productId,
        String sku,
        String productName,
        Long locationId,
        String locationCode,
        String locationArea,
        String deviceNo,
        int qty,
        int reservedQty,
        int availableQty,
        // 批次信息
        Long batchId,
        String itemKey,
        LocalDate productionDate,
        Integer shelfLifeDays,
        String manufacturer,
        LocalDate expiryDate
) {}
