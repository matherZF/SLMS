-- ============================================================
-- WCS 系统 AGV 小车设备档案（AGV01 ~ AGV40）
--
-- 来源：WCS 库（Oracle）WHLD_WCS.WCS_SUBTASKINFOHISTORY，
--       DEVICETYPE = 'AGV'，车号取 AGVCARNO，现场共 40 台，编号 1 ~ 40。
-- SP 侧编号约定：AGVCARNO 1 → AGV01 … 40 → AGV40（两位零填充）。
-- WCS 侧汇总脚本 docs/wcs-device-stat-daily.sql 用
--       'AGV' || LPAD(TO_CHAR(AGVCARNO), 2, '0')
-- 生成同样的编号，两边必须完全一致，否则设备详情页查不到运行数据。
--
-- 为什么要建：设备运行数据按 device_code 精确匹配 wms_equipment.code，
-- 档案里没有的设备不会报错，只表现为「详情页运行数据为空」，属于静默失败。
--
-- 堆垛机（SC01~SC12）不在这里建：它们的运行数据取数字孪生(DT)源，
-- 已由 V27__seed_ddj_stacker_equipments.sql 补齐。
--
-- 命名：name = N号AGV；model = AGV；status = RUNNING
-- 幂等：按 code 去重，已存在则跳过，不覆盖人工维护过的名称/位置/状态。
-- ============================================================

INSERT INTO wms_equipment (code, name, model, status, remark, created_at)
WITH RECURSIVE seq(n) AS (
  SELECT 1
  UNION ALL
  SELECT n + 1 FROM seq WHERE n < 40
)
SELECT CONCAT('AGV', LPAD(seq.n, 2, '0')),
       CONCAT(seq.n, '号AGV'),
       'AGV',
       'RUNNING',
       'WCS系统AGV小车（车号1~40）；数据来源 WCS 库 WCS_SUBTASKINFOHISTORY',
       NOW(6)
FROM seq
WHERE NOT EXISTS (
  SELECT 1 FROM wms_equipment e WHERE e.code = CONCAT('AGV', LPAD(seq.n, 2, '0'))
);
