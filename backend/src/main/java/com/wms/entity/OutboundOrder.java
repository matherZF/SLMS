package com.wms.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "wms_outbound_order", uniqueConstraints = {
        @UniqueConstraint(columnNames = "order_no")
})
public class OutboundOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String orderNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, columnDefinition = "varchar(32)")
    private OutboundStatus status;

    /** 本次领用对应的物流自动化设备；为空时表示普通领用。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id")
    private Equipment equipment;

    @Column(length = 64) private String equipmentCodeSnapshot;
    @Column(length = 128) private String equipmentNameSnapshot;

    @Column(length = 500)
    private String purpose;

    @Column(length = 64)
    private String applicant;

    @Column(length = 64) private String department;
    @Column(length = 64) private String recipient;
    @Column(length = 32) private String contactPhone;
    @Column(length = 64) private String workOrderNo;
    private LocalDate requiredDate;
    @Column(length = 128) private String deliveryLocation;
    @Column(length = 64) private String pickedBy;
    private Instant pickedAt;
    @Column(length = 64) private String completedBy;
    private Instant completedAt;
    @Column(length = 64, unique = true)
    private String completionKey;

    @Column(length = 64)
    private String reviewedBy;

    @Column(length = 500)
    private String reviewOpinion;

    private Instant reviewedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "outboundOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OutboundItem> items = new ArrayList<>();

    public OutboundOrder() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public OutboundStatus getStatus() { return status; }
    public void setStatus(OutboundStatus status) { this.status = status; }
    public Equipment getEquipment() { return equipment; }
    public void setEquipment(Equipment equipment) { this.equipment = equipment; }
    public String getEquipmentCodeSnapshot() { return equipmentCodeSnapshot; } public void setEquipmentCodeSnapshot(String v) { equipmentCodeSnapshot = v; }
    public String getEquipmentNameSnapshot() { return equipmentNameSnapshot; } public void setEquipmentNameSnapshot(String v) { equipmentNameSnapshot = v; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getApplicant() { return applicant; }
    public void setApplicant(String applicant) { this.applicant = applicant; }
    public String getDepartment() { return department; } public void setDepartment(String v) { department = v; }
    public String getRecipient() { return recipient; } public void setRecipient(String v) { recipient = v; }
    public String getContactPhone() { return contactPhone; } public void setContactPhone(String v) { contactPhone = v; }
    public String getWorkOrderNo() { return workOrderNo; } public void setWorkOrderNo(String v) { workOrderNo = v; }
    public LocalDate getRequiredDate() { return requiredDate; } public void setRequiredDate(LocalDate v) { requiredDate = v; }
    public String getDeliveryLocation() { return deliveryLocation; } public void setDeliveryLocation(String v) { deliveryLocation = v; }
    public String getPickedBy() { return pickedBy; } public void setPickedBy(String v) { pickedBy = v; }
    public Instant getPickedAt() { return pickedAt; } public void setPickedAt(Instant v) { pickedAt = v; }
    public String getCompletedBy() { return completedBy; } public void setCompletedBy(String v) { completedBy = v; }
    public Instant getCompletedAt() { return completedAt; } public void setCompletedAt(Instant v) { completedAt = v; }
    public String getCompletionKey() { return completionKey; } public void setCompletionKey(String v) { completionKey = v; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getReviewOpinion() { return reviewOpinion; }
    public void setReviewOpinion(String reviewOpinion) { this.reviewOpinion = reviewOpinion; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public List<OutboundItem> getItems() { return items; }
    public void setItems(List<OutboundItem> items) { this.items = items; }
}
