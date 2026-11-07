-- ============================================================
-- WCS 系统 RB 机械手设备档案（RB01 ~ RB08）
--
-- 来源：WCS 库（Oracle）WHLD_WCS.WCS_TASKINFO_HISTORY +
--       WMS_M_FINISHEDTASK（拆垛/拣选任务），现场共 8 台机械手。
-- 编号映射（WCS 侧汇总过程 docs/wcs-device-stat-daily.sql 同步使用，两边必须一致）：
--   TARGETID 5076/5078/5081 → RB01
--   TARGETID 5085/5087/5090 → RB02
--   TARGETID 5094/5096/5099 → RB03
--   TARGETID 5103/5105/5108 → RB04
--   TARGETID 5112/5114/5117 → RB05
--   TARGETID 5121/5123/5126 → RB06
--   TARGETID 5130/5132/5135 → RB07
--   TARGETID 5139/5141/5144 → RB08
--
-- 为什么要建：设备运行数据按 device_code 精确匹配 wms_equipment.code，
-- 档案里没有的设备不会报错，只表现为「详情页运行数据为空」，属于静默失败。
--
-- RB 的件数口径：SUM(WMS_M_FINISHEDTASK.DISASSEMBLY_NUM)，按 WMS 任务去重后汇总，
-- 这是 WCS 源里唯一带件数的设备类型（AGV 恒 0，件数由 WMS 源提供）。
--
-- 命名：name = N号机械手；model = RB；status = RUNNING
-- 幂等：按 code 去重，已存在则跳过，不覆盖人工维护过的名称/位置/状态。
-- ============================================================

INSERT INTO wms_equipment (code, name, model, status, remark, created_at)
WITH RECURSIVE seq(n) AS (
  SELECT 1
  UNION ALL
  SELECT n + 1 FROM seq WHERE n < 8
)
SELECT CONCAT('RB', LPAD(seq.n, 2, '0')),
       CONCAT(seq.n, '号机械手'),
       'RB',
       'RUNNING',
       'WCS系统机械手（TARGETID三号一组映射RB01~RB08）；数据来源 WCS 库 WCS_TASKINFO_HISTORY + WMS_M_FINISHEDTASK',
       NOW(6)
FROM seq
WHERE NOT EXISTS (
  SELECT 1 FROM wms_equipment e WHERE e.code = CONCAT('RB', LPAD(seq.n, 2, '0'))
);
