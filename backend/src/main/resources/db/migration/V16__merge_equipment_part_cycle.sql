-- ============================================================
-- 设备备件关联：预计寿命 + 消耗周期 合并为「更换周期(天)」
--
-- 两个字段业务上高度重叠（备件坏了就换，寿命=消耗周期），
-- 分开填既容易填不一致，也被代码当成可互换（lifeCycleDays ?? consumptionCycleDays）。
-- 合并后：补货点 = 安全库存 + 采购周期 ÷ 更换周期 × 单机数量。
--
-- 顺带删除只被死代码 recordUsage 写入的旧领用表与两个日期列，
-- 寿命跟踪由 wms_spare_part_usage 承担（状态 NEAR_DUE/DUE 已接入预警）。
-- ============================================================

-- 1. 合并数据：以消耗周期为准，为空或 0 时回落到预计寿命
UPDATE wms_equipment_part
   SET consumption_cycle_days = COALESCE(NULLIF(consumption_cycle_days, 0), NULLIF(life_cycle_days, 0));

-- 2. 改名 consumption_cycle_days → change_cycle_days，删掉 life_cycle_days
ALTER TABLE wms_equipment_part
    CHANGE COLUMN consumption_cycle_days change_cycle_days INT NULL COMMENT '更换周期(天)；与采购周期共同决定补货点',
    DROP COLUMN life_cycle_days;

-- 3. 删除只有死代码写入的日期列
ALTER TABLE wms_equipment_part
    DROP COLUMN last_used_date,
    DROP COLUMN next_expected_use_date;

-- 4. 删除旧的领用记录表（迁移前为 0 行，全项目无读取方）
DROP TABLE IF EXISTS wms_equipment_usage;
