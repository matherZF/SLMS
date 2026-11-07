package com.wms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "wms_device_stat_daily", uniqueConstraints = {
        @UniqueConstraint(name = "uk_device_stat_daily", columnNames = {"stat_date", "source_sys", "device_type", "device_code"})
}, indexes = {
        @Index(name = "idx_device_stat_daily_device", columnList = "device_code,stat_date"),
        @Index(name = "idx_device_stat_daily_date", columnList = "stat_date")
})
public class DeviceStatDaily {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;
    @Column(name = "source_sys", nullable = false, length = 16)
    private String sourceSys;
    @Column(name = "device_type", nullable = false, length = 32)
    private String deviceType;
    @Column(name = "device_code", nullable = false, length = 64)
    private String deviceCode;
    @Column(name = "running_hours", nullable = false, precision = 18, scale = 2)
    private BigDecimal runningHours;
    @Column(name = "task_count", nullable = false)
    private Long taskCount;
    @Column(name = "total_qty", nullable = false)
    private Long totalQty;
    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (collectedAt == null) collectedAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public LocalDate getStatDate() { return statDate; }
    public void setStatDate(LocalDate v) { statDate = v; }
    public String getSourceSys() { return sourceSys; }
    public void setSourceSys(String v) { sourceSys = v; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String v) { deviceType = v; }
    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String v) { deviceCode = v; }
    public BigDecimal getRunningHours() { return runningHours; }
    public void setRunningHours(BigDecimal v) { runningHours = v; }
    public Long getTaskCount() { return taskCount; }
    public void setTaskCount(Long v) { taskCount = v; }
    public Long getTotalQty() { return totalQty; }
    public void setTotalQty(Long v) { totalQty = v; }
    public Instant getCollectedAt() { return collectedAt; }
    public void setCollectedAt(Instant v) { collectedAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { updatedAt = v; }
}
