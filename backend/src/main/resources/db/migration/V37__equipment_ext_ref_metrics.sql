-- V37: 取数规则化——每条映射可选指标，取消"按设备编码全源全指标"的默认兜底
--
-- 背景：改造前设备取数是"设备编码 = 外部编码，所有源、三个指标都查一遍"，无法表达
-- "机械手任务数只认 WMS、件数只认 WCS"这类口径。现在取数完全由映射行决定：
-- 一台设备配了几行就按几行取，每行自己声明要时长/任务数/件数中的哪几个；
-- 同一指标被多行命中时合计。没配映射的设备不再取到任何数据。
ALTER TABLE wms_equipment_ext_ref
  ADD COLUMN take_hours TINYINT(1) NOT NULL DEFAULT 1 COMMENT '取运行时长 running_hours',
  ADD COLUMN take_tasks TINYINT(1) NOT NULL DEFAULT 1 COMMENT '取任务数 task_count',
  ADD COLUMN take_qty   TINYINT(1) NOT NULL DEFAULT 1 COMMENT '取件数 total_qty';

-- 存量回填：每台设备按 wms_device_stat_daily 里实际出现过的来源各建一条映射（三指标全开），
-- 保证"只认配置"上线瞬间，各设备取数结果与改造前完全一致（每个源的行都在、指标也都在，
-- 重复来源依旧按原逻辑合计）。之后由业务在设备编辑页按需勾掉不该取的指标。
-- INSERT IGNORE：同一 (source_sys, ext_code) 已有行时跳过，可重复执行。
INSERT IGNORE INTO wms_equipment_ext_ref (equipment_id, source_sys, ext_code, take_hours, take_tasks, take_qty)
SELECT e.id, d.source_sys, e.code, 1, 1, 1
FROM wms_equipment e
JOIN (SELECT DISTINCT device_code, source_sys FROM wms_device_stat_daily) d
  ON d.device_code = e.code;
