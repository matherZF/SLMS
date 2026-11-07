package com.wms.dto;

import java.math.BigDecimal;

/**
 * 设备运行实时视图的一行。
 * totalPieceQty 是可选列（视图有 total_piece_qty 时才有值），
 * 没有时由 {@code DeviceCumulativeService} 从每日汇总表累加补齐。
 */
public record DeviceRuntimeViewVO(
        String equipmentNo,
        BigDecimal totalRunningHours,
        Long totalTaskCount,
        Long totalPieceQty) {
}
