package com.wms.dto;

import com.wms.entity.SystemConfig;

/** 系统配置项（列表与编辑都用这一个结构） */
public record SystemConfigDTO(
        Long id,
        String configGroup,
        String configKey,
        String label,
        String configValue,
        String defaultValue,
        String valueType,
        String unit,
        int sortOrder,
        String remark,
        boolean enabled,
        String groupLabel) {
    public static SystemConfigDTO of(SystemConfig c) {
        // Map.of 建出来的不可变 map 不接受 null key，分组为空时要先挡掉
        String group = c.getConfigGroup() == null ? "" : c.getConfigGroup();
        return new SystemConfigDTO(c.getId(), group, c.getConfigKey(), c.getLabel(),
                c.getConfigValue(), c.getDefaultValue(), c.getValueType(), c.getUnit(),
                c.getSortOrder(), c.getRemark(), c.isEnabled(), GROUP_LABELS.getOrDefault(group, group));
    }

    public static final java.util.Map<String, String> GROUP_LABELS = java.util.Map.of(
            "SPARE_PART", "备件与预警");
}
