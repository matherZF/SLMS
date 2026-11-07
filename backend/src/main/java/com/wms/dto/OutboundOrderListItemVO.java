package com.wms.dto;

import java.time.Instant;

// 出库单列表项
public record OutboundOrderListItemVO(
        Long id,
        String orderNo,
        String status,
        int itemCount,
        String applicant,
        String department,
        String recipient,
        String purpose,
        Instant createdAt
) {}
