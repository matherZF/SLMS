-- ============================================================
-- 设备档案增加「区域」字段，并按型号给存量设备填默认区域
--
-- 用途：设备管理页默认按区域折叠分组展示（点区域行展开该区域设备明细）。
-- 默认值是按设备型号给的一版合理归类，页面上随时可改；
-- 新增设备不填区域时归到「未分区」组。
-- ============================================================

ALTER TABLE wms_equipment ADD COLUMN zone VARCHAR(64) NULL COMMENT '区域（页面按此折叠分组）' AFTER location;

-- 存量设备默认区域（按型号归类，人工可改）
UPDATE wms_equipment SET zone = '立体库'   WHERE zone IS NULL AND (model = '堆垛机' OR code LIKE 'SC%');
UPDATE wms_equipment SET zone = 'AGV区'    WHERE zone IS NULL AND model = 'AGV';
UPDATE wms_equipment SET zone = '机械手区' WHERE zone IS NULL AND model = 'RB';
UPDATE wms_equipment SET zone = '码垛区'   WHERE zone IS NULL AND model = 'DK';
UPDATE wms_equipment SET zone = '输送线'   WHERE zone IS NULL AND model IN ('STA', 'LBJ', 'DDJ');
UPDATE wms_equipment SET zone = '分拣线'   WHERE zone IS NULL AND model = 'FJX';
