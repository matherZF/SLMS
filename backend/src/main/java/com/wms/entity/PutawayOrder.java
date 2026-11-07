package com.wms.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "wms_putaway_order", uniqueConstraints = {
        @UniqueConstraint(columnNames = "order_no")
})
public class PutawayOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String orderNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** 关联批次（入库单新建时确定） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @Column(nullable = false)
    private int planQty;

    @Column(nullable = false)
    private int putQty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PutawayStatus status;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "putawayOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PutawayItem> items = new ArrayList<>();

    public PutawayOrder() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public Batch getBatch() { return batch; }
    public void setBatch(Batch batch) { this.batch = batch; }
    public int getPlanQty() { return planQty; }
    public void setPlanQty(int planQty) { this.planQty = planQty; }
    public void setPlanQty(Integer planQty) { this.planQty = planQty == null ? 0 : planQty; }
    public int getPutQty() { return putQty; }
    public void setPutQty(int putQty) { this.putQty = putQty; }
    public PutawayStatus getStatus() { return status; }
    public void setStatus(PutawayStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public List<PutawayItem> getItems() { return items; }
    public void setItems(List<PutawayItem> items) { this.items = items; }
}
