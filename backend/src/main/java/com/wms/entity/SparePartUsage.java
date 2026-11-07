package com.wms.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name="wms_spare_part_usage", uniqueConstraints=@UniqueConstraint(name="uk_usage_outbound_item",columnNames="outbound_item_id"),
        indexes={@Index(name="idx_usage_product",columnList="product_id"),@Index(name="idx_usage_equipment",columnList="equipment_code"),@Index(name="idx_usage_status",columnList="status")})
public class SparePartUsage {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=64,unique=true) private String usageNo;
 @Column(nullable=false) private Long outboundOrderId; @Column(nullable=false,length=64) private String outboundOrderNo;
 @Column(nullable=false) private Long outboundItemId; @Column(nullable=false) private Long productId;
 @Column(nullable=false,length=64) private String sku; @Column(nullable=false,length=128) private String productName;
 @Column(length=255) private String spec; @Column(length=32) private String unit;
 private Long batchId; @Column(length=64) private String batchNo; @Column(nullable=false) private int qty;
 @Column(nullable=false,columnDefinition="int not null default 0") private int activeQty;
 @Column(nullable=false,columnDefinition="int not null default 0") private int returnedQty;
 private Long equipmentId; @Column(length=64) private String equipmentCode; @Column(length=128) private String equipmentName;
 @Column(nullable=false,length=16) private String lifeMetric; @Column(precision=18,scale=2) private BigDecimal ratedLifeValue;
 /** 领用时快照的多计量规则 JSON，第一条为主口径 */
 @Column(columnDefinition="text") private String lifeMetricRules;
 /** @deprecated 已停用：百分比判据提前量随寿命长短漂移，改用 {@link #warningDays} 按剩余天数判断 */
 @Deprecated
 @Column(nullable=false,columnDefinition="int not null default 80") private int warningPercent=80;
 /** 寿命预警提前天数，建档时快照自备件档案；0 表示跟随系统配置 */
 @Column(nullable=false,columnDefinition="int not null default 0") private int warningDays=0;
 @Column(precision=18,scale=2) private BigDecimal baselineRunningHours; private Long baselineTaskCount;
 @Column(precision=18,scale=2) private BigDecimal currentRunningHours; private Long currentTaskCount;
 /** QTY 口径：设备累计处理件数的基准值与当前值 */
 private Long baselinePieceQty; private Long currentPieceQty;
 /** 预测：主导口径的日均消耗、拟合样本跨度、按速率推出的寿命耗尽日 */
 @Column(precision=18,scale=4) private BigDecimal dailyRate; private Integer rateSampleDays; private Instant expectedEndAt;
 /** 预测：出库时从设备-备件关联快照的更换周期，以及据此推算的计划更换日 */
 private Integer plannedCycleDays; private Instant plannedEndAt;
 /** 建议更换日：寿命耗尽日与计划更换日取早 */
 private Instant expectedReplaceAt;
 @Column(nullable=false,length=24) private String status; @Column(nullable=false,length=24) private String baselineStatus;
 @Column(length=500) private String dataError; @Column(nullable=false) private Instant startedAt; private Instant lastSyncedAt;
 private Instant endedAt; @Column(length=500) private String endReason;
 @CreationTimestamp @Column(updatable=false) private Instant createdAt;
 public Long getId(){return id;} public String getUsageNo(){return usageNo;} public void setUsageNo(String v){usageNo=v;}
 public Long getOutboundOrderId(){return outboundOrderId;} public void setOutboundOrderId(Long v){outboundOrderId=v;} public String getOutboundOrderNo(){return outboundOrderNo;} public void setOutboundOrderNo(String v){outboundOrderNo=v;}
 public Long getOutboundItemId(){return outboundItemId;} public void setOutboundItemId(Long v){outboundItemId=v;} public Long getProductId(){return productId;} public void setProductId(Long v){productId=v;}
 public String getSku(){return sku;} public void setSku(String v){sku=v;} public String getProductName(){return productName;} public void setProductName(String v){productName=v;} public String getSpec(){return spec;} public void setSpec(String v){spec=v;} public String getUnit(){return unit;} public void setUnit(String v){unit=v;}
 public Long getBatchId(){return batchId;} public void setBatchId(Long v){batchId=v;} public String getBatchNo(){return batchNo;} public void setBatchNo(String v){batchNo=v;} public int getQty(){return qty;} public void setQty(int v){qty=v;} public int getActiveQty(){return activeQty;} public void setActiveQty(int v){activeQty=v;} public int getReturnedQty(){return returnedQty;} public void setReturnedQty(int v){returnedQty=v;}
 public Long getEquipmentId(){return equipmentId;} public void setEquipmentId(Long v){equipmentId=v;} public String getEquipmentCode(){return equipmentCode;} public void setEquipmentCode(String v){equipmentCode=v;} public String getEquipmentName(){return equipmentName;} public void setEquipmentName(String v){equipmentName=v;}
 public String getLifeMetric(){return lifeMetric;} public void setLifeMetric(String v){lifeMetric=v;} public BigDecimal getRatedLifeValue(){return ratedLifeValue;} public void setRatedLifeValue(BigDecimal v){ratedLifeValue=v;} public int getWarningPercent(){return warningPercent;} public void setWarningPercent(int v){warningPercent=v;} public int getWarningDays(){return warningDays;} public void setWarningDays(int v){warningDays=v;}
 public BigDecimal getBaselineRunningHours(){return baselineRunningHours;} public void setBaselineRunningHours(BigDecimal v){baselineRunningHours=v;} public Long getBaselineTaskCount(){return baselineTaskCount;} public void setBaselineTaskCount(Long v){baselineTaskCount=v;}
 public BigDecimal getCurrentRunningHours(){return currentRunningHours;} public void setCurrentRunningHours(BigDecimal v){currentRunningHours=v;} public Long getCurrentTaskCount(){return currentTaskCount;} public void setCurrentTaskCount(Long v){currentTaskCount=v;}
 public String getLifeMetricRules(){return lifeMetricRules;} public void setLifeMetricRules(String v){lifeMetricRules=v;}
 public Long getBaselinePieceQty(){return baselinePieceQty;} public void setBaselinePieceQty(Long v){baselinePieceQty=v;} public Long getCurrentPieceQty(){return currentPieceQty;} public void setCurrentPieceQty(Long v){currentPieceQty=v;}
 public BigDecimal getDailyRate(){return dailyRate;} public void setDailyRate(BigDecimal v){dailyRate=v;} public Integer getRateSampleDays(){return rateSampleDays;} public void setRateSampleDays(Integer v){rateSampleDays=v;} public Instant getExpectedEndAt(){return expectedEndAt;} public void setExpectedEndAt(Instant v){expectedEndAt=v;}
 public Integer getPlannedCycleDays(){return plannedCycleDays;} public void setPlannedCycleDays(Integer v){plannedCycleDays=v;} public Instant getPlannedEndAt(){return plannedEndAt;} public void setPlannedEndAt(Instant v){plannedEndAt=v;}
 public Instant getExpectedReplaceAt(){return expectedReplaceAt;} public void setExpectedReplaceAt(Instant v){expectedReplaceAt=v;}
 public String getStatus(){return status;} public void setStatus(String v){status=v;} public String getBaselineStatus(){return baselineStatus;} public void setBaselineStatus(String v){baselineStatus=v;} public String getDataError(){return dataError;} public void setDataError(String v){dataError=v;}
 public Instant getStartedAt(){return startedAt;} public void setStartedAt(Instant v){startedAt=v;} public Instant getLastSyncedAt(){return lastSyncedAt;} public void setLastSyncedAt(Instant v){lastSyncedAt=v;} public Instant getEndedAt(){return endedAt;} public void setEndedAt(Instant v){endedAt=v;} public String getEndReason(){return endReason;} public void setEndReason(String v){endReason=v;} public Instant getCreatedAt(){return createdAt;}
}
