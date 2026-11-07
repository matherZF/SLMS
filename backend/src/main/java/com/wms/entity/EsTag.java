package com.wms.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * 电子标签（墨水屏）基础信息。
 * <p>
 * MAC 为业务主键，{@code location_id} 指向绑定的库位（= {@link Location#getId()}）。
 * 运行时信息（电量、在线、固件版本、最后通信时间）可通过 EslService 从墨水屏服务器同步。
 */
@Entity
@Table(name = "wms_es_tag", uniqueConstraints = @UniqueConstraint(columnNames = "mac"))
public class EsTag {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 标签 MAC 地址，业务唯一 */
    @Column(nullable = false, length = 64)
    private String mac;

    @Column(length = 128)
    private String name;

    @Column(nullable = false, length = 32)
    private String type = "LIGHT_LED";

    @Column(name = "firmware_version", length = 64)
    private String firmwareVersion;

    /** 剩余电量百分比 0~100 */
    private Integer power;

    /** 在线状态 0=离线 1=在线 */
    @Column(nullable = false)
    private boolean online;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    /** 绑定的库位 ID，= Location.id */
    @Column(name = "location_id")
    private Long locationId;

    @Column(length = 255)
    private String remark;

    @Column(nullable = false, length = 32)
    private String status = "ACTIVE";

    @CreationTimestamp @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public Long getId() { return id; }
    public String getMac() { return mac; }
    public void setMac(String mac) { this.mac = mac; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }
    public Integer getPower() { return power; }
    public void setPower(Integer power) { this.power = power; }
    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
