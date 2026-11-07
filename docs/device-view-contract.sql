-- 由设备系统数据库管理员创建。
-- 字段别名是备品备件系统的固定读取契约；底层表名和字段名按设备系统实际结构替换。
--
-- 说明：
--  * 前 3 列是必需列，缺任一列备件寿命的"运行小时/任务次数"口径无法计算。
--  * TOTAL_PIECE_QTY（累计处理件数）是【可选列】。备件系统启动时会探测视图有没有这一列：
--      - 有  → QTY 口径直接取这里的实时累计值；
--      - 没有 → 自动回落到 SP 库每日汇总表 wms_device_stat_daily 按设备累加 total_qty。
--    因此不改视图也能用件数口径（前提是 ext-stat 同步已开启、源库已提供件数），
--    想更实时再补这一列。
CREATE OR REPLACE VIEW vw_sp_equipment_runtime AS
SELECT
    device_code          AS equipment_no,
    accumulated_hours    AS total_running_hours,
    accumulated_tasks    AS total_task_count,
    accumulated_qty      AS total_piece_qty      -- 可选：累计处理件数
FROM equipment_runtime_source;

-- 建议仅授予视图查询权限：
-- GRANT SELECT ON equipment.vw_sp_equipment_runtime TO 'spare_parts_reader'@'<备件系统服务器IP>';

-- 老视图升级（已经建过、只想补件数列时执行；Oracle 用 CREATE OR REPLACE 直接重建即可）
-- ALTER 不适用视图，改完 SELECT 语句重新 CREATE OR REPLACE VIEW 即可。
