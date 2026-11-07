package com.wms.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "wms_stocktake_order", uniqueConstraints = @UniqueConstraint(columnNames = "order_no"))
public class StocktakeOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 64)
    private String orderNo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StocktakeStatus status;
    @Column(length = 255)
    private String remark;
    @Column(length = 64)
    private String createdBy;
    @Column(length = 64)
    private String completedBy;
    private Instant completedAt;
    @CreationTimestamp @Column(updatable = false)
    private Instant createdAt;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StocktakeItem> items = new ArrayList<>();

    public Long getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public StocktakeStatus getStatus() { return status; }
    public void setStatus(StocktakeStatus status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getCompletedBy() { return completedBy; }
    public void setCompletedBy(String completedBy) { this.completedBy = completedBy; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public List<StocktakeItem> getItems() { return items; }
}
