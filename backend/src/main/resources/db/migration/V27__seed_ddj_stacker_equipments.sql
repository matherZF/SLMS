-- ============================================================
-- DT 系统堆垛机设备档案补齐（SC02 ~ SC12）
--
-- 来源：10.156.191.33 / dt-system.dt_device_state，device_type_key='DDJ'
-- 该类型在源库里是 12 台堆垛机，device_number 为 001 ~ 012。
-- SP 侧编号约定：device_number 001 → SC01（已存在：1号堆垛机），
--                依此类推 002 → SC02 ... 012 → SC12。
-- 同步 SQL 里用 concat('SC', right(device_number,2)) 完成这个转换。
--
-- 为什么必须建齐：设备运行数据按 device_code 精确匹配 wms_equipment.code，
-- 档案里没有的设备不会报错，只会表现为「查不到数」，属于静默失败。
--
-- 命名：name = N号堆垛机；model = 堆垛机（与既有 SC01 保持一致）
-- 幂等：按 code 去重，已存在则跳过，不覆盖人工维护过的名称/状态。
-- ============================================================

INSERT INTO wms_equipment (code, name, model, status, remark, created_at)
SELECT t.code,
       t.name,
       '堆垛机',
       'RUNNING',
       'DT系统设备类型 DDJ（堆垛机）；数据来源 dt-system.dt_device_state',
       NOW(6)
FROM (
  SELECT 'SC02' AS code, '2号堆垛机'  AS name
  UNION ALL SELECT 'SC03', '3号堆垛机'
  UNION ALL SELECT 'SC04', '4号堆垛机'
  UNION ALL SELECT 'SC05', '5号堆垛机'
  UNION ALL SELECT 'SC06', '6号堆垛机'
  UNION ALL SELECT 'SC07', '7号堆垛机'
  UNION ALL SELECT 'SC08', '8号堆垛机'
  UNION ALL SELECT 'SC09', '9号堆垛机'
  UNION ALL SELECT 'SC10', '10号堆垛机'
  UNION ALL SELECT 'SC11', '11号堆垛机'
  UNION ALL SELECT 'SC12', '12号堆垛机'
) t
WHERE NOT EXISTS (SELECT 1 FROM wms_equipment e WHERE e.code = t.code);
