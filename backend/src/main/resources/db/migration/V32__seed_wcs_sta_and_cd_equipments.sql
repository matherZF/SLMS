-- ============================================================
-- WCS 系统 STA 站台 + DK 码垛（CD 编号）设备档案补齐
--
-- 来源：2026-11-06 实查 WCS 库视图 whld_wcs.vw_sp_device_stat_daily
--       （device_type='STA' 65 个站台、device_type='DK' 的 CD1~CD8），
--       与 SP 设备表 wms_equipment 按 code 精确比对后的差集。
--
-- 比对结果：
--   AGV01~AGV40 / RB01~RB08：SP 已全有，无需处理；
--   STA：SP 只有 6 个（5160/5161/5168/5174/5180/5186，即 1~6号入库口），
--        本迁移补齐其余 59 个站台（5001~5293，WCS 编号即设备号）；
--   DK：SP 已有 MD3/MD4/MD7/MD8（WMS 源编号），WCS 侧另一套编号 CD1~CD8，
--       按现场决定全部建档并存（CD3/CD4 暂无数据也一并建好，避免静默漏数）。
--       ★ 注意：若 CD 与 MD 实为同一批码垛机，两套档案会导致同一台设备
--         双源重复计数（WMS 件数 + WCS 任务数），需现场确认对应关系。
--
-- 编码来源：STA = WCS_SUBTASKINFOHISTORY.FROMNODE（存储过程第 3 段），
--           与 docs/wcs-device-stat-daily.sql 的口径一致，两边必须同步改。
-- 幂等：按 code 去重，已存在则跳过，不覆盖人工维护过的名称/位置/状态。
-- ============================================================

-- ---------- STA 站台（59 个，编号不规则用 JSON_TABLE 展开） ----------
INSERT INTO wms_equipment (code, name, model, status, remark, created_at)
SELECT j.code,
       CONCAT('站台', j.code),
       'STA',
       'RUNNING',
       'WCS系统站台/输送线（FROMNODE 站位号即设备号）；数据来源 WCS 库',
       NOW(6)
FROM JSON_TABLE(
  '["5001","5004","5007","5010","5013","5016","5019","5022","5025","5028",
    "5031","5034","5037","5040","5043","5046","5049","5052","5055","5058",
    "5061","5064","5067","5070","5075","5080","5084","5089","5090","5099",
    "5108","5111","5116","5117","5120","5125","5129","5134","5138","5143",
    "5150","5192","5193","5194","5211","5214","5216","5230","5233","5235",
    "5249","5253","5255","5269","5271","5273","5289","5291","5293"]',
  '$[*]' COLUMNS (code VARCHAR(8) PATH '$')
) j
WHERE NOT EXISTS (
  SELECT 1 FROM wms_equipment e WHERE e.code = j.code
);

-- ---------- DK 码垛（CD1~CD8，与 MD 系列并存） ----------
INSERT INTO wms_equipment (code, name, model, status, remark, created_at)
WITH RECURSIVE seq(n) AS (
  SELECT 1
  UNION ALL
  SELECT n + 1 FROM seq WHERE n < 8
)
SELECT CONCAT('CD', seq.n),
       CONCAT('码垛机CD', seq.n),
       'DK',
       'RUNNING',
       'WCS系统码垛设备（CD编号体系，与WMS源的MD3/MD4/MD7/MD8并存；若实为同一批设备需现场确认对应关系，防止双源重复计数）',
       NOW(6)
FROM seq
WHERE NOT EXISTS (
  SELECT 1 FROM wms_equipment e WHERE e.code = CONCAT('CD', seq.n)
);
