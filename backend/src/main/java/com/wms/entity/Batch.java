package com.wms.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 批次：由生产时间、保质期、生产厂商等信息共同构成，与库存绑定。
 * item_key 为批次唯一标识（入库单新建时输入或自动生成）。
 */
@Entity
@Table(name = "wms_batch", uniqueConstraints = {
        @UniqueConstraint(columnNames = "item_key")
})
public class Batch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 批次号（唯一键） */
    @Column(name = "item_key", nullable = false, length = 64)
    private String itemKey;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(length = 64)
    private String sku;

    @Column(length = 128)
    private String productName;

    /** 生产日期 */
    @Column(name = "production_date")
    private LocalDate productionDate;

    /** 保质期（天） */
    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

    /** 生产厂商 */
    @Column(length = 128)
    private String manufacturer;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public Batch() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getItemKey() { return itemKey; }
    public void setItemKey(String itemKey) { this.itemKey = itemKey; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public LocalDate getProductionDate() { return productionDate; }
    public void setProductionDate(LocalDate productionDate) { this.productionDate = productionDate; }
    public Integer getShelfLifeDays() { return shelfLifeDays; }
    public void setShelfLifeDays(Integer shelfLifeDays) { this.shelfLifeDays = shelfLifeDays; }
    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    /** 到期日 = 生产日期 + 保质期天数 */
    public LocalDate getExpiryDate() {
        if (productionDate == null || shelfLifeDays == null) return null;
        return productionDate.plusDays(shelfLifeDays);
    }
}
