package com.wms.dto;
import java.math.BigDecimal; import java.time.Instant; import java.util.List;
/**
 * 备件使用台账。
 *
 * <p>顶层的 lifeMetric / ratedLifeValue / usedLifeValue / usedPercent 语义是<b>当前主导口径</b>
 * （多口径取严后百分比最高的那条），这样列表、导出、告警等既有展示无需改动就是"最该关注的数字"。
 * 各口径的完整明细见 {@code lifeMetrics}。</p>
 *
 * <p>末尾六个字段是更换预测，语义见 {@code V18__usage_replacement_forecast.sql}：
 * dailyRate 为日均消耗，expectedEndAt 为寿命耗尽日，plannedCycleDays/plannedEndAt 为设备关联里
 * 填的更换周期及其推算的计划更换日，expectedReplaceAt 是两者取早的建议更换日。</p>
 */
public record SparePartUsageVO(Long id,String usageNo,Long outboundOrderId,String outboundOrderNo,Long outboundItemId,
 Long productId,String sku,String productName,String spec,String unit,Long batchId,String batchNo,int qty,int activeQty,int returnedQty,
 Long equipmentId,String equipmentCode,String equipmentName,String lifeMetric,BigDecimal ratedLifeValue,int warningPercent,int warningDays,
 BigDecimal baselineRunningHours,Long baselineTaskCount,BigDecimal currentRunningHours,Long currentTaskCount,
 BigDecimal usedLifeValue,BigDecimal remainingLifeValue,Integer usedPercent,String status,String baselineStatus,
 String dataError,Instant startedAt,Instant lastSyncedAt,Instant endedAt,String endReason,Instant createdAt,
 List<LifeMetricUsageVO> lifeMetrics,Long baselinePieceQty,Long currentPieceQty,
 BigDecimal dailyRate,Integer rateSampleDays,Instant expectedEndAt,
 Integer plannedCycleDays,Instant plannedEndAt,Instant expectedReplaceAt){}
