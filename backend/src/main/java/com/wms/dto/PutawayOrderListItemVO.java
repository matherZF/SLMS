package com.wms.dto;

import java.time.Instant;

public record PutawayOrderListItemVO(
        Long id,
        String orderNo,
        Long productId,
        String sku,
        String productName,
        Integer planQty,
        Integer putQty,
        String status,
        Integer progress,
        Instant createdAt
) {
}
