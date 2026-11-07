package com.wms.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "wms_location", uniqueConstraints = {
        @UniqueConstraint(columnNames = "code")
})
public class Location {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(length = 64)
    private String area;

    @Column(length = 64)
    private String type;

    @Column(length = 128)
    private String warehouseName;

    @Column(length = 16)
    private String storageMode;

    private Integer rackNo;

    private Integer levelNo;

    private Integer positionNo;

    @Column(nullable = false, columnDefinition = "bit not null default 1")
    private boolean enabled = true;

    // 绑定的灯光提示设备编号（预留，用于后续接入硬件）
    @Column(length = 64)
    private String deviceNo;

    @Column(length = 255)
    private String remark;

    public Location() {}
    public Location(String code, String area, String type, String remark) {
        this.code = code;
        this.area = area;
        this.type = type;
        this.remark = remark;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getWarehouseName() { return warehouseName; }
    public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }
    public String getStorageMode() { return storageMode; }
    public void setStorageMode(String storageMode) { this.storageMode = storageMode; }
    public Integer getRackNo() { return rackNo; }
    public void setRackNo(Integer rackNo) { this.rackNo = rackNo; }
    public Integer getLevelNo() { return levelNo; }
    public void setLevelNo(Integer levelNo) { this.levelNo = levelNo; }
    public Integer getPositionNo() { return positionNo; }
    public void setPositionNo(Integer positionNo) { this.positionNo = positionNo; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getDeviceNo() { return deviceNo; }
    public void setDeviceNo(String deviceNo) { this.deviceNo = deviceNo; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
