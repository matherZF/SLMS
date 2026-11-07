package com.wms.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

/**
 * 系统配置项。
 *
 * <p>原先备件相关的可调参数（预警提前量、安全天数、默认交期等）都写死在 {@code application.yml}，
 * 改一次要重新打包发版。搬到这里之后运营可以在「系统管理 → 系统配置」里直接改，即时生效。</p>
 *
 * <p>代码里仍保留同名环境变量作为出厂默认值：表里查不到该键时回落到它，
 * 所以配置项被误删也不会导致启动失败。</p>
 */
@Entity
@Table(name = "wms_system_config", indexes = @Index(name = "idx_sysconfig_group", columnList = "config_group,enabled"))
public class SystemConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_group", nullable = false, length = 32)
    private String configGroup;

    @Column(name = "config_key", nullable = false, unique = true, length = 64)
    private String configKey;

    @Column(nullable = false, length = 128)
    private String label;

    @Column(name = "config_value", nullable = false, length = 255)
    private String configValue;

    @Column(name = "default_value", nullable = false, length = 255)
    private String defaultValue;

    @Column(name = "value_type", nullable = false, length = 16)
    private String valueType = "INT";

    @Column(length = 16)
    private String unit;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(length = 255)
    private String remark;

    @Column(nullable = false)
    private boolean enabled = true;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public String getConfigGroup() { return configGroup; }
    public void setConfigGroup(String v) { configGroup = v; }
    public String getConfigKey() { return configKey; }
    public void setConfigKey(String v) { configKey = v; }
    public String getLabel() { return label; }
    public void setLabel(String v) { label = v; }
    public String getConfigValue() { return configValue; }
    public void setConfigValue(String v) { configValue = v; }
    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String v) { defaultValue = v; }
    public String getValueType() { return valueType; }
    public void setValueType(String v) { valueType = v; }
    public String getUnit() { return unit; }
    public void setUnit(String v) { unit = v; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int v) { sortOrder = v; }
    public String getRemark() { return remark; }
    public void setRemark(String v) { remark = v; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }
    public Instant getUpdatedAt() { return updatedAt; }
}
