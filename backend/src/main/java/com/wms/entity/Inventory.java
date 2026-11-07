package com.wms.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "wms_inventory", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"product_id", "location_id", "batch_id"})
})
public class Inventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 防止并发入库/出库时后提交的事务覆盖先提交的库存变更。 */
    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    /** 关联批次 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @Column(nullable = false)
    private int qty;

    /** 已被拣货单占用、尚未正式出库的数量。 */
    @Column(nullable = false, columnDefinition = "int not null default 0")
    private int reservedQty;

    @UpdateTimestamp
    private Instant updatedAt;

    public Inventory() {}
    public Inventory(Product product, Location location, int qty, Instant updatedAt) {
        this.product = product;
        this.location = location;
        this.qty = qty;
        this.updatedAt = updatedAt;
    }
    public Inventory(Product product, Location location, Batch batch, int qty, Instant updatedAt) {
        this.product = product;
        this.location = location;
        this.batch = batch;
        this.qty = qty;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }
    public Batch getBatch() { return batch; }
    public void setBatch(Batch batch) { this.batch = batch; }
    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }
    public int getReservedQty() { return reservedQty; }
    public void setReservedQty(int reservedQty) { this.reservedQty = reservedQty; }
    public int getAvailableQty() { return Math.max(0, qty - reservedQty); }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
