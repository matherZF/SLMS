package com.wms.dto;

import java.time.Instant;

public record PutawayItemVO(
        Long id,
        Long locationId,
        String locationCode,
        String area,
        Integer qty,
        Instant createdAt
) {
}
