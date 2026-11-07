package com.wms.dto;

import java.time.Instant;

/** 接口日志详情：包含完整收发报文。 */
public record InterfaceLogVO(Long id, String direction, String interfaceCode, String interfaceName,
                             String channel, String peer, String requestBody, String responseBody,
                             boolean success, String errorMessage, String traceId, Long durationMs,
                             Instant createdAt) {}
