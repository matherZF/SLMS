package com.wms.entity;

// 出库单状态：待拣货 / 拣货中 / 已完成
public enum OutboundStatus {
    PENDING_APPROVAL, // 待审批
    APPROVED,        // 已批准，待仓库执行
    PICKING,         // 拣货中
    DONE,            // 已完成
    REJECTED,        // 已驳回
    CANCELLED        // 已取消
}
