package com.wms.dto;

import java.time.Instant;

/** 接口日志列表项：不返回报文体，避免列表响应过大。 */
public record InterfaceLogSummaryVO(Long id, String direction, String interfaceCode, String interfaceName,
                                    String channel, String peer, boolean success, String errorMessage,
                                    String traceId, Long durationMs, Instant createdAt) {}
