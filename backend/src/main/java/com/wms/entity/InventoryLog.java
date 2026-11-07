package com.wms.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * 库存变动日志：记录每一次库存的入库/出库变动明细
 */
@Entity
@Table(name = "wms_inventory_log", indexes = {
        @Index(name = "idx_log_product", columnList = "product_id"),
        @Index(name = "idx_log_location", columnList = "location_id"),
        @Index(name = "idx_log_change_type", columnList = "change_type"),
        @Index(name = "idx_log_ref_no", columnList = "ref_no"),
        @Index(name = "idx_log_created", columnList = "created_at")
})
public class InventoryLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(length = 128)
    private String productName;

    @Column(length = 64)
    private String sku;

    @Column(name = "location_id")
    private Long locationId;

    @Column(length = 64)
    private String locationCode;

    /** 变动类型：入库、出库、退库、报废、盘点调整、移库转出/转入。 */
    @Column(name = "change_type", nullable = false, length = 16)
    private String changeType;

    /** 变动数量（正数） */
    @Column(nullable = false)
    private int changeQty;

    /** 变动前库存 */
    @Column(nullable = false)
    private int beforeQty;

    /** 变动后库存 */
    @Column(nullable = false)
    private int afterQty;

    /** 关联单据类型：PUTAWAY / OUTBOUND */
    @Column(length = 32)
    private String refType;

    /** 关联单号 */
    @Column(length = 64)
    private String refNo;

    /** 操作人 */
    @Column(length = 64)
    private String operator;

    /** 备注 */
    @Column(length = 255)
    private String remark;

    // ===== 批次信息 =====
    @Column(length = 64)
    private String itemKey;

    @Column(name = "production_date")
    private java.time.LocalDate productionDate;

    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

    @Column(length = 128)
    private String manufacturer;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public InventoryLog() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public String getLocationCode() { return locationCode; }
    public void setLocationCode(String locationCode) { this.locationCode = locationCode; }
    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }
    public int getChangeQty() { return changeQty; }
    public void setChangeQty(int changeQty) { this.changeQty = changeQty; }
    public int getBeforeQty() { return beforeQty; }
    public void setBeforeQty(int beforeQty) { this.beforeQty = beforeQty; }
    public int getAfterQty() { return afterQty; }
    public void setAfterQty(int afterQty) { this.afterQty = afterQty; }
    public String getRefType() { return refType; }
    public void setRefType(String refType) { this.refType = refType; }
    public String getRefNo() { return refNo; }
    public void setRefNo(String refNo) { this.refNo = refNo; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getItemKey() { return itemKey; }
    public void setItemKey(String itemKey) { this.itemKey = itemKey; }
    public java.time.LocalDate getProductionDate() { return productionDate; }
    public void setProductionDate(java.time.LocalDate productionDate) { this.productionDate = productionDate; }
    public Integer getShelfLifeDays() { return shelfLifeDays; }
    public void setShelfLifeDays(Integer shelfLifeDays) { this.shelfLifeDays = shelfLifeDays; }
    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
