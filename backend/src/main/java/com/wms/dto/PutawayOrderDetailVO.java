package com.wms.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PutawayOrderDetailVO(
        Long id,
        String orderNo,
        Long productId,
        String sku,
        String productName,
        Integer planQty,
        Integer putQty,
        String status,
        Integer progress,
        Integer remainingQty,
        List<PutawayItemVO> items,
        Instant createdAt,
        // 批次信息
        String itemKey,
        LocalDate productionDate,
        Integer shelfLifeDays,
        String manufacturer,
        LocalDate expiryDate
) {
}
