package com.wms.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "wms_operation_audit_log", indexes = {
        @Index(name = "idx_audit_business", columnList = "business_type,business_id"),
        @Index(name = "idx_audit_created_at", columnList = "created_at")
})
public class OperationAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 32) private String businessType;
    @Column(nullable = false) private Long businessId;
    @Column(length = 64) private String businessNo;
    @Column(nullable = false, length = 32) private String action;
    @Column(length = 32) private String fromStatus;
    @Column(length = 32) private String toStatus;
    @Column(length = 64) private String operator;
    @Column(length = 1000) private String detail;
    @CreationTimestamp @Column(updatable = false) private Instant createdAt;

    public Long getId() { return id; }
    public String getBusinessType() { return businessType; } public void setBusinessType(String v) { businessType = v; }
    public Long getBusinessId() { return businessId; } public void setBusinessId(Long v) { businessId = v; }
    public String getBusinessNo() { return businessNo; } public void setBusinessNo(String v) { businessNo = v; }
    public String getAction() { return action; } public void setAction(String v) { action = v; }
    public String getFromStatus() { return fromStatus; } public void setFromStatus(String v) { fromStatus = v; }
    public String getToStatus() { return toStatus; } public void setToStatus(String v) { toStatus = v; }
    public String getOperator() { return operator; } public void setOperator(String v) { operator = v; }
    public String getDetail() { return detail; } public void setDetail(String v) { detail = v; }
    public Instant getCreatedAt() { return createdAt; }
}
