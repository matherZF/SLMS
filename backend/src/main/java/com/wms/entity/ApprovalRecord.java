package com.wms.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "wms_approval_record", indexes = {
        @Index(name = "idx_approval_business", columnList = "businessType,businessId"),
        @Index(name = "idx_approval_status", columnList = "status")
})
public class ApprovalRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 32) private String businessType;
    @Column(nullable = false) private Long businessId;
    @Column(nullable = false, length = 64) private String businessNo;
    @Column(nullable = false) private Integer roundNo;
    @Column(nullable = false, length = 64) private String applicant;
    @Column(length = 64) private String approver;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16, columnDefinition = "varchar(16)") private ApprovalStatus status;
    @Column(length = 500) private String opinion;
    @Column(nullable = false) private Instant submittedAt;
    private Instant handledAt;
    private Long workflowDefinitionId;
    private Integer workflowVersion;
    @Column(length=1000) private String matchReason;
    private Integer nodeSequence;
    @Column(length=64) private String nodeName;
    @Column(length=16) private String approverType;
    @Column(length=64) private String approverValue;
    @CreationTimestamp @Column(updatable = false) private Instant createdAt;

    public Long getId() { return id; } public void setId(Long v) { id=v; }
    public String getBusinessType() { return businessType; } public void setBusinessType(String v) { businessType=v; }
    public Long getBusinessId() { return businessId; } public void setBusinessId(Long v) { businessId=v; }
    public String getBusinessNo() { return businessNo; } public void setBusinessNo(String v) { businessNo=v; }
    public Integer getRoundNo() { return roundNo; } public void setRoundNo(Integer v) { roundNo=v; }
    public String getApplicant() { return applicant; } public void setApplicant(String v) { applicant=v; }
    public String getApprover() { return approver; } public void setApprover(String v) { approver=v; }
    public ApprovalStatus getStatus() { return status; } public void setStatus(ApprovalStatus v) { status=v; }
    public String getOpinion() { return opinion; } public void setOpinion(String v) { opinion=v; }
    public Instant getSubmittedAt() { return submittedAt; } public void setSubmittedAt(Instant v) { submittedAt=v; }
    public Instant getHandledAt() { return handledAt; } public void setHandledAt(Instant v) { handledAt=v; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getWorkflowDefinitionId(){return workflowDefinitionId;}public void setWorkflowDefinitionId(Long v){workflowDefinitionId=v;}public Integer getWorkflowVersion(){return workflowVersion;}public void setWorkflowVersion(Integer v){workflowVersion=v;}public String getMatchReason(){return matchReason;}public void setMatchReason(String v){matchReason=v;}public Integer getNodeSequence(){return nodeSequence;}public void setNodeSequence(Integer v){nodeSequence=v;}public String getNodeName(){return nodeName;}public void setNodeName(String v){nodeName=v;}public String getApproverType(){return approverType;}public void setApproverType(String v){approverType=v;}public String getApproverValue(){return approverValue;}public void setApproverValue(String v){approverValue=v;}
}
