package com.wms.dto;

import java.time.Instant;

/**
 * 统一站内消息条目：预警、待审批、待拣货、盘点进行中都用这个结构。
 * 前端根据 category 差异化渲染/跳转。
 *
 * <p>read 是"当前用户是否已读"：已读的消息仍留在列表里，只是不再出红点、不计数。
 * 点开即已读，所以弹框里能同时看到已读和未读，和常见 APP 的消息中心一致。</p>
 */
public record NotificationItem(
        String category,      // ALERT / APPROVAL / PICKING / STOCKTAKE
        String severity,      // HIGH / MEDIUM / INFO
        String title,         // 标题行
        String subtitle,      // 副标题 / 关键标识（单号/申请人等）
        String message,       // 完整描述
        String route,         // 点击跳转路由
        Long targetId,        // 可选：跳转时定位到某个目标（预警 id / 审批 businessId / 出库单 id）
        Instant createdAt,    // 产生时间
        boolean read          // 当前用户是否已读
) {
    /** 只改已读状态，其余字段原样保留 */
    public NotificationItem withRead(boolean value) {
        return new NotificationItem(category, severity, title, subtitle, message, route, targetId, createdAt, value);
    }
}
