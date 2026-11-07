package com.wms.dto;

import java.time.Instant;
import java.util.List;

// 出库单详情视图
public record OutboundOrderDetailVO(
        Long id,
        String orderNo,
        String status,
        List<OutboundItemVO> items,
        Instant createdAt,
        String applicant, String department, String recipient, String contactPhone,
        String purpose, String workOrderNo, java.time.LocalDate requiredDate, String deliveryLocation,
        Long equipmentId, String equipmentCode, String equipmentName,
        String reviewedBy, String reviewOpinion, Instant reviewedAt,
        String pickedBy, Instant pickedAt, String completedBy, Instant completedAt,
        List<ApprovalRecordVO> approvalRecords,
        List<OperationAuditLogVO> auditLogs,
        List<InventoryLogVO> inventoryLogs
) {}
