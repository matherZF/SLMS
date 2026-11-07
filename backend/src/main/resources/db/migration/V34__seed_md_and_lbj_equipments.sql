-- ============================================================
-- 设备档案补齐：DK 码垛机 MD1/MD2/MD5/MD6 + 链板机 LBJ9/LBJ10
--
-- 来源：2026-11-06 实查 WMS 生产库（10.156.191.25/ldwl_wms）视图
--       vw_sp_device_stat_daily 的 DEVICE_TYPE / DEVICE_ID 分布，
--       与 SP 设备表 wms_equipment 按 code 精确比对后的差集。
--
-- 现场确认：DK 类型有两套编号，CD1~CD8 与 MD1~MD8 并存且都要统计：
--   · CD 系列 → 数据来自 WCS（device_type='DK'，任务数/件数）
--   · MD 系列 → 数据来自 WMS（PIECE_QTY 件数）
--   因此两套档案都保留，不做合并；本迁移只补齐 MD 缺的 4 台。
--
-- 幂等：按 code 去重，已存在则跳过，不覆盖人工维护过的名称/位置/状态/区域。
-- ============================================================

-- ---------- ① DK 码垛机：MD1 / MD2 / MD5 / MD6 ----------
INSERT INTO wms_equipment (code, name, model, zone, status, remark, created_at)
SELECT j.code,
       CONCAT('码垛机', j.code),
       'DK',
       '码垛区',
       'RUNNING',
       'WMS 源 DK 类型设备（与 CD1~CD8 为两套编号，并存统计）；数据来源 WMS 生产库',
       NOW(6)
FROM JSON_TABLE('["MD1","MD2","MD5","MD6"]',
                '$[*]' COLUMNS (code VARCHAR(16) PATH '$')) j
WHERE NOT EXISTS (SELECT 1 FROM wms_equipment e WHERE e.code = j.code);

-- ---------- ② 链板机：LBJ1 ~ LBJ10（现场确认编号是 1-10 连续） ----------
-- WMS 视图目前只出现 LBJ1/2/7/8/9/10，现场说明是 1~10 共 10 台，
-- 尚未上报的 LBJ3/4/5/6 也一并建好，避免以后有数据时成了孤儿。
INSERT INTO wms_equipment (code, name, model, zone, status, remark, created_at)
SELECT j.code,
       CONCAT(j.code, '号链板机'),
       'LBJ',
       '输送线',
       'RUNNING',
       'WMS 源 LBJ 类型设备；数据来源 WMS 生产库',
       NOW(6)
FROM JSON_TABLE('["LBJ1","LBJ2","LBJ3","LBJ4","LBJ5","LBJ6","LBJ7","LBJ8","LBJ9","LBJ10"]',
                '$[*]' COLUMNS (code VARCHAR(16) PATH '$')) j
WHERE NOT EXISTS (SELECT 1 FROM wms_equipment e WHERE e.code = j.code);
