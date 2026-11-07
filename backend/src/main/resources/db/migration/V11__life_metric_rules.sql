-- V11: 寿命计量从"单口径"升级为"多口径取严"
-- 背景：设备不是连续运行的，同一备件的失效机理往往不止一个维度。
--   例如行走减速机：厂家定额既是"运行 8000 小时"，又有"启停 20 万次"；
--   输送线滚轮/链条：按"处理件数"磨损更准。
-- 做法：每个备件可配多条计量规则，各规则有各自的额定值（单位不同），
--   系统按 "已用% = max(各口径已用/各自额定)" 取最严的一条作为主导口径，
--   效果等同汽车保养"里程到了或时间到了，谁先到保养谁"。
--
-- 兼容：life_metric / rated_life_value 两列保留，语义收敛为"主口径"（= 规则第一条），
--   列表、导出、告警等既有读取路径无需改动；规则为空时按旧的单口径换算。

ALTER TABLE wms_product
    ADD COLUMN life_metric_rules TEXT NULL
        COMMENT '多计量口径规则(JSON)：[{"metric":"HOURS","rated":2000},{"metric":"QTY","rated":50000}]，第一条为主口径';

ALTER TABLE wms_spare_part_usage
    ADD COLUMN life_metric_rules TEXT NULL
        COMMENT '领用时从备件主数据快照的多计量规则(JSON)，使用台账建立后不再跟随主数据变化',
    ADD COLUMN baseline_piece_qty BIGINT NULL
        COMMENT '开始使用时的设备累计处理件数（QTY 口径基准）',
    ADD COLUMN current_piece_qty BIGINT NULL
        COMMENT '最近一次刷新的设备累计处理件数';

-- 采集快照也记下"这次是哪个口径主导"，事后追溯时能看出当时是被小时数还是件数顶到期的
ALTER TABLE wms_spare_part_usage_snapshot
    ADD COLUMN life_metric VARCHAR(16) NULL
        COMMENT '本次采集时占主导的计量口径（取严结果）',
    ADD COLUMN piece_qty BIGINT NULL
        COMMENT '本次采集时的设备累计处理件数';

-- 存量数据回填：把已有的单口径配置转成一条规则，行为保持不变
UPDATE wms_product
SET life_metric_rules = CONCAT('[{"metric":"', life_metric, '","rated":', rated_life_value, '}]')
WHERE life_metric IS NOT NULL
  AND life_metric <> 'NONE'
  AND rated_life_value IS NOT NULL
  AND (life_metric_rules IS NULL OR life_metric_rules = '');

UPDATE wms_spare_part_usage
SET life_metric_rules = CONCAT('[{"metric":"', life_metric, '","rated":', rated_life_value, '}]')
WHERE life_metric IS NOT NULL
  AND life_metric <> 'NONE'
  AND rated_life_value IS NOT NULL
  AND (life_metric_rules IS NULL OR life_metric_rules = '');
