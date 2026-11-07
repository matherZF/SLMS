package com.wms.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "wms_putaway_item")
public class PutawayItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "putaway_order_id", nullable = false)
    private PutawayOrder putawayOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(nullable = false)
    private int qty;

    /**
     * 上架确认幂等键（前端每次点击生成一个 UUID）。
     * <p>
     * 移动端网络抖动时用户会重复提交，或者干脆双击确认；没有幂等键的话
     * 同一次"上架 5 件"会被记成两次 5 件，库存直接翻倍。
     * 数据库上有唯一键兜底，业务层先查一次命中则直接返回上次的结果。
     */
    @Column(name = "request_id", length = 64)
    private String requestId;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    public PutawayItem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public PutawayOrder getPutawayOrder() { return putawayOrder; }
    public void setPutawayOrder(PutawayOrder putawayOrder) { this.putawayOrder = putawayOrder; }
    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }
    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
