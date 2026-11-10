-- V36: 设备 ↔ 外部系统设备编码映射表（1:N）
-- 背景：WCS 托盘线任务按起点站台(FROMNODE)统计，双向线 A->B 记 A、B->A 记 B，
--       一台物理线体在源系统里有多个编码；SP 设备档案一台设备要能对上多个外部编码。
-- 原始数据 wms_device_stat_daily 仍按外部编码颗粒保留，映射只发生在查询/累计时
-- （查询时按映射聚合，改映射立刻生效，历史数据不用动）。
-- 唯一键 (source_sys, ext_code)：防止同一外部编码挂到两台设备上造成双计。
CREATE TABLE IF NOT EXISTS wms_equipment_ext_ref (
  id           BIGINT       AUTO_INCREMENT PRIMARY KEY,
  equipment_id BIGINT       NOT NULL COMMENT 'SP 设备档案 wms_equipment.id',
  source_sys   VARCHAR(16)  NOT NULL COMMENT '外部系统：WCS / WMS / DT，对应 wms_device_stat_daily.source_sys',
  ext_code     VARCHAR(64)  NOT NULL COMMENT '外部系统的设备编码（如双向托盘线另一端站台 STA002）',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_equipment_ext_ref (source_sys, ext_code),
  KEY idx_equipment_ext_ref_equipment (equipment_id),
  CONSTRAINT fk_equipment_ext_ref_equipment FOREIGN KEY (equipment_id) REFERENCES wms_equipment (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备与外部系统编码映射（一台SP设备可对应多个外部编码）';
