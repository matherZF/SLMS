package com.wms.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * 设备 ↔ 外部系统设备编码映射（1:N）。
 *
 * <p>一台 SP 设备（如双向托盘线）可能对应外部系统的多个编码（线体两端的站台），
 * 设备档案的 {@code code} 只挂其中一个主编码，其余编码放这里。
 * 运行数据查询/累计时把映射编码一起聚合，原始数据 {@code wms_device_stat_daily}
 * 仍按外部编码颗粒保留。</p>
 *
 * <p>唯一键 (source_sys, ext_code) 保证一个外部编码只能挂一台设备，防止双计。</p>
 */
@Entity
@Table(name = "wms_equipment_ext_ref", uniqueConstraints = @UniqueConstraint(columnNames = {"source_sys", "ext_code"}))
public class EquipmentExtRef {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @ManyToOne(optional = false) @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    /** 外部系统：WCS / WMS / DT，对应 wms_device_stat_daily.source_sys */
    @Column(nullable = false, length = 16) private String sourceSys;

    /** 外部系统的设备编码 */
    @Column(name = "ext_code", nullable = false, length = 64) private String extCode;

    /** 取运行时长 running_hours */
    @Column(name = "take_hours", nullable = false) private boolean takeHours = true;

    /** 取任务数 task_count */
    @Column(name = "take_tasks", nullable = false) private boolean takeTasks = true;

    /** 取件数 total_qty */
    @Column(name = "take_qty", nullable = false) private boolean takeQty = true;

    @CreationTimestamp @Column(updatable = false) private Instant createdAt;

    public Long getId() { return id; }
    public Equipment getEquipment() { return equipment; }
    public void setEquipment(Equipment v) { equipment = v; }
    public String getSourceSys() { return sourceSys; }
    public void setSourceSys(String v) { sourceSys = v; }
    public String getExtCode() { return extCode; }
    public void setExtCode(String v) { extCode = v; }
    public boolean isTakeHours() { return takeHours; }
    public void setTakeHours(boolean v) { takeHours = v; }
    public boolean isTakeTasks() { return takeTasks; }
    public void setTakeTasks(boolean v) { takeTasks = v; }
    public boolean isTakeQty() { return takeQty; }
    public void setTakeQty(boolean v) { takeQty = v; }
    public Instant getCreatedAt() { return createdAt; }
}
