package com.wms.dto;

import java.time.Instant;

public record OperationAuditLogVO(Long id, String businessType, Long businessId, String businessNo,
                                  String action, String fromStatus, String toStatus,
                                  String operator, String detail, Instant createdAt) {}
