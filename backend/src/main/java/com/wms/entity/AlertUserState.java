package com.wms.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 预警用户维度状态：已读/忽略各用户独立，不互相影响。
 */
@Entity
@Table(name = "wms_alert_user_state", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"alertId", "userId"})
})
public class AlertUserState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_id", nullable = false)
    private Long alertId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "read_flag", nullable = false, columnDefinition = "bit not null default 0")
    private boolean readFlag = false;

    @Column(nullable = false, columnDefinition = "bit not null default 0")
    private boolean ignored = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAlertId() { return alertId; }
    public void setAlertId(Long alertId) { this.alertId = alertId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public boolean isReadFlag() { return readFlag; }
    public void setReadFlag(boolean readFlag) { this.readFlag = readFlag; }
    public boolean isIgnored() { return ignored; }
    public void setIgnored(boolean ignored) { this.ignored = ignored; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
