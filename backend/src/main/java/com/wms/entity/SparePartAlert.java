package com.wms.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 备件预警记录：每天定时算一次落在这里，页面读表而不实时算。
 *
 * <p>一条记录对应一个「预警对象」（{@code dedupKey}），处理掉之后不再自动重开，
 * 除非情况恶化（MEDIUM 升 HIGH）。这样现场才不会每天被同一件事反复打扰。</p>
 */
@Entity
@Table(name = "wms_spare_part_alert")
public class SparePartAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private LocalDate alertDate;
    @Column(nullable = false, length = 16)
    private String alertType;
    @Column(length = 16)
    private String scope;
    private Long productId;
    @Column(length = 64)
    private String sku;
    @Column(length = 128)
    private String productName;
    private Long equipmentId;
    @Column(length = 64)
    private String equipmentCode;
    @Column(length = 128)
    private String equipmentName;
    private Long usageId;
    private Long inventoryId;
    @Column(nullable = false, length = 16)
    private String severity;
    private Integer stockQty;
    private Integer thresholdQty;
    private Integer safetyStock;
    @Column(precision = 18, scale = 4)
    private BigDecimal dailyDemand;
    private Integer coverageDays;
    private LocalDate stockoutDate;
    private LocalDate receiveByDate;
    private LocalDate orderByDate;
    private Integer suggestedQty;
    private Integer procurementLeadDays;
    @Column(nullable = false, columnDefinition = "bit not null default 0")
    private boolean leadEstimated = false;
    @Column(nullable = false, length = 500)
    private String message;
    @Column(nullable = false, length = 16)
    private String status = "OPEN";
    @Column(nullable = false, length = 200)
    private String dedupKey;
    /** 处理人（ACK 时填充） */
    private Long handlerId;
    @Column(length = 128)
    private String handlerName;
    private Instant handledAt;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    public Long getId() { return id; }
    public LocalDate getAlertDate() { return alertDate; }
    public void setAlertDate(LocalDate v) { alertDate = v; }
    public String getAlertType() { return alertType; }
    public void setAlertType(String v) { alertType = v; }
    public String getScope() { return scope; }
    public void setScope(String v) { scope = v; }
    public Long getProductId() { return productId; }
    public void setProductId(Long v) { productId = v; }
    public String getSku() { return sku; }
    public void setSku(String v) { sku = v; }
    public String getProductName() { return productName; }
    public void setProductName(String v) { productName = v; }
    public Long getEquipmentId() { return equipmentId; }
    public void setEquipmentId(Long v) { equipmentId = v; }
    public String getEquipmentCode() { return equipmentCode; }
    public void setEquipmentCode(String v) { equipmentCode = v; }
    public String getEquipmentName() { return equipmentName; }
    public void setEquipmentName(String v) { equipmentName = v; }
    public Long getUsageId() { return usageId; }
    public void setUsageId(Long v) { usageId = v; }
    public Long getInventoryId() { return inventoryId; }
    public void setInventoryId(Long v) { inventoryId = v; }
    public String getSeverity() { return severity; }
    public void setSeverity(String v) { severity = v; }
    public Integer getStockQty() { return stockQty; }
    public void setStockQty(Integer v) { stockQty = v; }
    public Integer getThresholdQty() { return thresholdQty; }
    public void setThresholdQty(Integer v) { thresholdQty = v; }
    public Integer getSafetyStock() { return safetyStock; }
    public void setSafetyStock(Integer v) { safetyStock = v; }
    public BigDecimal getDailyDemand() { return dailyDemand; }
    public void setDailyDemand(BigDecimal v) { dailyDemand = v; }
    public Integer getCoverageDays() { return coverageDays; }
    public void setCoverageDays(Integer v) { coverageDays = v; }
    public LocalDate getStockoutDate() { return stockoutDate; }
    public void setStockoutDate(LocalDate v) { stockoutDate = v; }
    public LocalDate getReceiveByDate() { return receiveByDate; }
    public void setReceiveByDate(LocalDate v) { receiveByDate = v; }
    public LocalDate getOrderByDate() { return orderByDate; }
    public void setOrderByDate(LocalDate v) { orderByDate = v; }
    public Integer getSuggestedQty() { return suggestedQty; }
    public void setSuggestedQty(Integer v) { suggestedQty = v; }
    public Integer getProcurementLeadDays() { return procurementLeadDays; }
    public void setProcurementLeadDays(Integer v) { procurementLeadDays = v; }
    public boolean isLeadEstimated() { return leadEstimated; }
    public void setLeadEstimated(boolean v) { leadEstimated = v; }
    public String getMessage() { return message; }
    public void setMessage(String v) { message = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public Long getHandlerId() { return handlerId; }
    public void setHandlerId(Long v) { handlerId = v; }
    public String getHandlerName() { return handlerName; }
    public void setHandlerName(String v) { handlerName = v; }
    public Instant getHandledAt() { return handledAt; }
    public void setHandledAt(Instant v) { handledAt = v; }
    public String getDedupKey() { return dedupKey; }
    public void setDedupKey(String v) { dedupKey = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { updatedAt = v; }
}
