-- 备件使用台账增加「下次更换」预测字段。
--
-- 两条预测依据并存，互不覆盖：
--   1) expected_end_at  —— 物理寿命耗尽日，由历史快照拟合的日均消耗外推（DAYS 口径为开始使用+额定天数）
--   2) planned_end_at   —— 计划更换日，由设备-备件关联里的更换周期推算
-- expected_replace_at 取两者中较早的一个，即"建议更换日"：实际消耗比计划快就提前，反之按预防性计划执行。
--
-- 历史台账的 planned_cycle_days 不在此回填：刷新台账时会按当前设备关联补查并落库，
-- 避免在迁移里写跨表 UPDATE（刷新逻辑本身也需要这条补查路径来处理新建/换设备的场景）。

ALTER TABLE `wms_spare_part_usage`
  ADD COLUMN `daily_rate` decimal(18,4) DEFAULT NULL,
  ADD COLUMN `rate_sample_days` int DEFAULT NULL,
  ADD COLUMN `expected_end_at` datetime(6) DEFAULT NULL,
  ADD COLUMN `planned_cycle_days` int DEFAULT NULL,
  ADD COLUMN `planned_end_at` datetime(6) DEFAULT NULL,
  ADD COLUMN `expected_replace_at` datetime(6) DEFAULT NULL;

ALTER TABLE `wms_spare_part_usage` ADD INDEX `idx_usage_replace_at` (`expected_replace_at`);
