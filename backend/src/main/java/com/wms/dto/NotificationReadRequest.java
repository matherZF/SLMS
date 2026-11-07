package com.wms.dto;

import java.time.Instant;

/**
 * 标记单条消息已读的入参。
 * 服务端优先用实时数据生成快照；只有源数据已经消失（例如刚处理完的审批）时才用请求里的字段兜底，
 * 所以这些快照字段都是可选的。
 */
public record NotificationReadRequest(
        String category,
        Long targetId,
        String severity,
        String title,
        String subtitle,
        String message,
        String route,
        Instant createdAt
) {}
