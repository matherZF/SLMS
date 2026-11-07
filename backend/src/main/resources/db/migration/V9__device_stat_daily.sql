-- V9: 设备每日运行数据汇总表（SP 侧落地表）
-- 数据来源：后台定时任务每天从外部系统（WCS / WMS）暴露的同构只读视图拉取前一天数据。
-- source_sys 区分来源系统，便于后续接入更多外部系统（不仅仅是 WCS，还有 WMS）。
-- 唯一键 (stat_date, source_sys, device_type, device_code) 保证重复拉取是覆盖而不是插重。
CREATE TABLE IF NOT EXISTS wms_device_stat_daily (
  id             BIGINT         AUTO_INCREMENT PRIMARY KEY,
  stat_date      DATE           NOT NULL COMMENT '统计日期（按源系统任务创建时间 CREATETIME 归属）',
  source_sys     VARCHAR(16)    NOT NULL COMMENT '来源系统：WCS / WMS',
  device_type    VARCHAR(32)    NOT NULL COMMENT '设备类型：SC=堆垛机，AGV=AGV小车，由源系统提供',
  device_code    VARCHAR(64)    NOT NULL COMMENT '设备编号（SC 取 DEVICEID，AGV 取 AGVCARNO）',
  running_hours  DECIMAL(18,2)  NOT NULL DEFAULT 0 COMMENT '当日作业时长（小时）= SUM((FINISHEDTIME-SENDTIME)*24)',
  task_count     BIGINT         NOT NULL DEFAULT 0 COMMENT '当日完成任务数',
  collected_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次采集时间',
  updated_at     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  UNIQUE KEY uk_device_stat_daily (stat_date, source_sys, device_type, device_code),
  KEY idx_device_stat_daily_device (device_code, stat_date),
  KEY idx_device_stat_daily_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备每日运行数据汇总（从外部系统定时同步）';
