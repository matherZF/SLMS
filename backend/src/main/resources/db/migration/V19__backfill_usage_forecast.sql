-- 回填历史台账的更换预测字段。
--
-- V18 只是加了列，写值发生在两个入口：出库建单、每次刷新。V18 之前建的台账没走过这些分支，
-- 页面上的「预计更换」列是空的，用户得手动把每条都刷一遍才会出现日期。
--
-- 这里能补的只有不依赖设备数据的部分：
--   1) DAYS 口径的寿命耗尽日 = 开始使用 + 额定天数，随时可算
--   2) 计划更换日 = 开始使用 + 设备关联里填的更换周期
-- HOURS/TASKS/QTY 口径必须靠多天快照拟合速率，这里不补齐、也不编造，
-- 留给下一次人工刷新或夜间自动同步来完成。

UPDATE wms_spare_part_usage
SET expected_end_at = DATE_ADD(started_at, INTERVAL CAST(rated_life_value AS SIGNED) DAY)
WHERE expected_end_at IS NULL
  AND life_metric = 'DAYS'
  AND rated_life_value > 0
  AND status NOT IN ('CLOSED', 'RETURNED');

UPDATE wms_spare_part_usage u
SET planned_cycle_days = (
        SELECT ep.change_cycle_days
        FROM wms_equipment_part ep
        WHERE ep.equipment_id = u.equipment_id
          AND ep.product_id = u.product_id
    )
WHERE planned_cycle_days IS NULL
  AND equipment_id IS NOT NULL
  AND status NOT IN ('CLOSED', 'RETURNED');

UPDATE wms_spare_part_usage
SET planned_end_at = DATE_ADD(started_at, INTERVAL planned_cycle_days DAY)
WHERE planned_end_at IS NULL
  AND planned_cycle_days > 0;

UPDATE wms_spare_part_usage
SET expected_replace_at = expected_end_at
WHERE expected_replace_at IS NULL
  AND expected_end_at IS NOT NULL;

UPDATE wms_spare_part_usage
SET expected_replace_at = planned_end_at
WHERE planned_end_at IS NOT NULL
  AND (expected_replace_at IS NULL OR planned_end_at < expected_replace_at);
