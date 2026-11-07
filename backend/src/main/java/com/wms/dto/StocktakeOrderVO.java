package com.wms.dto;

import java.time.Instant;
import java.util.List;

public record StocktakeOrderVO(Long id, String orderNo, String status, String remark,
                               String createdBy, Instant createdAt, String completedBy,
                               Instant completedAt, int itemCount, int countedCount,
                               List<StocktakeItemVO> items) {}
