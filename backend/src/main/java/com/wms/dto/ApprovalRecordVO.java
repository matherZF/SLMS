package com.wms.dto;

import java.time.Instant;

public record ApprovalRecordVO(Long id, String businessType, Long businessId, String businessNo,
                               Integer roundNo, String applicant, String approver, String status,
                               String opinion, Instant submittedAt, Instant handledAt,
                               Integer nodeSequence,String nodeName,String approverType,String approverValue,
                               Integer workflowVersion,String matchReason) {}
