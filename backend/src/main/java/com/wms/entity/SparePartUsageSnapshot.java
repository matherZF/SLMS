package com.wms.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "wms_spare_part_usage_snapshot", indexes = {
        @Index(name = "idx_usage_snapshot_usage_time", columnList = "usage_id,collected_at")
})
public class SparePartUsageSnapshot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long usageId;
    @Column(nullable = false, length = 16) private String source;
    @Column(precision = 18, scale = 2) private BigDecimal runningHours;
    private Long taskCount;
    private Long pieceQty;
    /** 本次采集时占主导的计量口径（多口径取严的结果） */
    @Column(length = 16) private String lifeMetric;
    @Column(precision = 18, scale = 2) private BigDecimal usedLifeValue;
    @Column(precision = 18, scale = 2) private BigDecimal remainingLifeValue;
    private Integer usedPercent;
    @Column(nullable = false, length = 24) private String status;
    @Column(nullable = false) private Instant collectedAt;

    public Long getId() { return id; }
    public Long getUsageId() { return usageId; } public void setUsageId(Long v) { usageId = v; }
    public String getSource() { return source; } public void setSource(String v) { source = v; }
    public BigDecimal getRunningHours() { return runningHours; } public void setRunningHours(BigDecimal v) { runningHours = v; }
    public Long getTaskCount() { return taskCount; } public void setTaskCount(Long v) { taskCount = v; }
    public Long getPieceQty() { return pieceQty; } public void setPieceQty(Long v) { pieceQty = v; }
    public String getLifeMetric() { return lifeMetric; } public void setLifeMetric(String v) { lifeMetric = v; }
    public BigDecimal getUsedLifeValue() { return usedLifeValue; } public void setUsedLifeValue(BigDecimal v) { usedLifeValue = v; }
    public BigDecimal getRemainingLifeValue() { return remainingLifeValue; } public void setRemainingLifeValue(BigDecimal v) { remainingLifeValue = v; }
    public Integer getUsedPercent() { return usedPercent; } public void setUsedPercent(Integer v) { usedPercent = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public Instant getCollectedAt() { return collectedAt; } public void setCollectedAt(Instant v) { collectedAt = v; }
}
