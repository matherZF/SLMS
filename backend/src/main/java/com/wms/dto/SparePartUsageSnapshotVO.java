package com.wms.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record SparePartUsageSnapshotVO(Long id, String source, BigDecimal runningHours, Long taskCount,
        BigDecimal usedLifeValue, BigDecimal remainingLifeValue, Integer usedPercent,
        String status, Instant collectedAt, String lifeMetric, Long pieceQty) {}
