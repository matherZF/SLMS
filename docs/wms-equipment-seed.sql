-- ============================================================
-- WMS 设备档案初始化（设备号与 WMS dws_device_daily_stat.DEVICE_ID 一致）
--
-- 来源：WMS 测试库 10.156.191.24 / ldwl_wms.dws_device_daily_stat
-- 用途：SP 的件数（QTY）口径按 device_code 精确匹配 wms_equipment.code，
--       设备档案缺号不会报错，只会表现为「无数据」，所以必须先把档案建齐。
--
-- 命名约定：code = name = WMS 设备号；model = WMS 设备类型代码；
--           类型中文名待现场确认后可用 UPDATE 批量补到 name 前。
-- 幂等：按 code 去重，重复执行只刷新 model，不改人工维护过的 name/status。
-- ============================================================

INSERT INTO wms_equipment (code, name, model, status, remark, created_at) VALUES
  ('5201', '5201', 'DDJ', 'RUNNING', 'WMS设备类型 DDJ；数据来源 ldwl_wms.dws_device_daily_stat', NOW(6)),
  ('5220', '5220', 'DDJ', 'RUNNING', 'WMS设备类型 DDJ；数据来源 ldwl_wms.dws_device_daily_stat', NOW(6)),
  ('5239', '5239', 'DDJ', 'RUNNING', 'WMS设备类型 DDJ；数据来源 ldwl_wms.dws_device_daily_stat', NOW(6)),
  ('5259', '5259', 'DDJ', 'RUNNING', 'WMS设备类型 DDJ；数据来源 ldwl_wms.dws_device_daily_stat', NOW(6)),
  ('5279', '5279', 'DDJ', 'RUNNING', 'WMS设备类型 DDJ；数据来源 ldwl_wms.dws_device_daily_stat', NOW(6)),
  ('5298', '5298', 'DDJ', 'RUNNING', 'WMS设备类型 DDJ；数据来源 ldwl_wms.dws_device_daily_stat', NOW(6)),
  ('5160', '5160', 'TPX', 'RUNNING', 'WMS设备类型 TPX；数据来源 ldwl_wms.dws_device_daily_stat', NOW(6)),
  ('5161', '5161', 'TPX', 'RUNNING', 'WMS设备类型 TPX；数据来源 ldwl_wms.dws_device_daily_stat', NOW(6)),
  ('5168', '5168', 'TPX', 'RUNNING', 'WMS设备类型 TPX；数据来源 ldwl_wms.dws_device_daily_stat', NOW(6)),
  ('5174', '5174', 'TPX', 'RUNNING', 'WMS设备类型 TPX；数据来源 ldwl_wms.dws_device_daily_stat', NOW(6)),
  ('5180', '5180', 'TPX', 'RUNNING', 'WMS设备类型 TPX；数据来源 ldwl_wms.dws_device_daily_stat', NOW(6)),
  ('5186', '5186', 'TPX', 'RUNNING', 'WMS设备类型 TPX；数据来源 ldwl_wms.dws_device_daily_stat', NOW(6)),
  ('MD3', 'MD3', 'DK', 'RUNNING', 'WMS设备类型 DK；数据来源 ldwl_wms.dws_device_daily_stat', NOW(6)),
  ('MD4', 'MD4', 'DK', 'RUNNING', 'WMS设备类型 DK；数据来源 ldwl_wms.dws_device_daily_stat', NOW(6)),
  ('MD7', 'MD7', 'DK', 'RUNNING', 'WMS设备类型 DK；数据来源 ldwl_wms.dws_device_daily_stat', NOW(6)),
  ('MD8', 'MD8', 'DK', 'RUNNING', 'WMS设备类型 DK；数据来源 ldwl_wms.dws_device_daily_stat', NOW(6)),
  ('SO20', 'SO20', 'FJX', 'RUNNING', 'WMS设备类型 FJX；数据来源 ldwl_wms.dws_device_daily_stat', NOW(6)),
  ('LBJ1', 'LBJ1', 'LBJ', 'RUNNING', 'WMS设备类型 LBJ；数据来源 ldwl_wms.dws_device_daily_stat', NOW(6)),
  ('LBJ2', 'LBJ2', 'LBJ', 'RUNNING', 'WMS设备类型 LBJ；数据来源 ldwl_wms.dws_device_daily_stat', NOW(6))
ON DUPLICATE KEY UPDATE model = VALUES(model);

-- 自检：应返回 19 行，且每行的 model 与源表 DEVICE_TYPE 一致
-- SELECT code, name, model, status FROM wms_equipment ORDER BY model, code;

-- 类型中文名确认后批量改名（示例，按实际口径替换）
-- UPDATE wms_equipment SET name = CONCAT('堆垛机', code) WHERE model = 'DDJ';

-- 回滚
-- DELETE FROM wms_equipment WHERE remark LIKE 'WMS设备类型%';
