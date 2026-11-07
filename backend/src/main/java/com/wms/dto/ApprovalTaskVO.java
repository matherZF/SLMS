package com.wms.dto;

import java.time.Instant;
import java.util.Map;

public record ApprovalTaskVO(
        String businessType, Long businessId, String businessNo, String applicant,
        String nodeName, String approverType, String approverValue,
        Integer workflowVersion, String matchReason, Instant submittedAt,
        Instant dueAt, long waitingHours, boolean overdue,
        Map<String, Object> businessSnapshot
) {}
