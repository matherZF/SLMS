-- 备品备件间 货架区 库位初始化（3 排 × 15 列 × 3 层 = 135 个）
-- 编码规则：排-列(两位)-层；rack_no=排 position_no=列 level_no=层
-- id 从 13 起（13 为手动添加的 1-01-1，脚本含该条，执行前请确认无重复）
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(13, '货架区', '1-01-1', NULL, NULL, '普通', 1, 1, 1, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(14, '货架区', '1-02-1', NULL, NULL, '普通', 1, 1, 2, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(15, '货架区', '1-03-1', NULL, NULL, '普通', 1, 1, 3, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(16, '货架区', '1-04-1', NULL, NULL, '普通', 1, 1, 4, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(17, '货架区', '1-05-1', NULL, NULL, '普通', 1, 1, 5, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(18, '货架区', '1-06-1', NULL, NULL, '普通', 1, 1, 6, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(19, '货架区', '1-07-1', NULL, NULL, '普通', 1, 1, 7, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(20, '货架区', '1-08-1', NULL, NULL, '普通', 1, 1, 8, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(21, '货架区', '1-09-1', NULL, NULL, '普通', 1, 1, 9, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(22, '货架区', '1-10-1', NULL, NULL, '普通', 1, 1, 10, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(23, '货架区', '1-11-1', NULL, NULL, '普通', 1, 1, 11, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(24, '货架区', '1-12-1', NULL, NULL, '普通', 1, 1, 12, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(25, '货架区', '1-13-1', NULL, NULL, '普通', 1, 1, 13, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(26, '货架区', '1-14-1', NULL, NULL, '普通', 1, 1, 14, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(27, '货架区', '1-15-1', NULL, NULL, '普通', 1, 1, 15, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(28, '货架区', '1-01-2', NULL, NULL, '普通', 1, 2, 1, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(29, '货架区', '1-02-2', NULL, NULL, '普通', 1, 2, 2, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(30, '货架区', '1-03-2', NULL, NULL, '普通', 1, 2, 3, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(31, '货架区', '1-04-2', NULL, NULL, '普通', 1, 2, 4, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(32, '货架区', '1-05-2', NULL, NULL, '普通', 1, 2, 5, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(33, '货架区', '1-06-2', NULL, NULL, '普通', 1, 2, 6, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(34, '货架区', '1-07-2', NULL, NULL, '普通', 1, 2, 7, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(35, '货架区', '1-08-2', NULL, NULL, '普通', 1, 2, 8, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(36, '货架区', '1-09-2', NULL, NULL, '普通', 1, 2, 9, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(37, '货架区', '1-10-2', NULL, NULL, '普通', 1, 2, 10, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(38, '货架区', '1-11-2', NULL, NULL, '普通', 1, 2, 11, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(39, '货架区', '1-12-2', NULL, NULL, '普通', 1, 2, 12, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(40, '货架区', '1-13-2', NULL, NULL, '普通', 1, 2, 13, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(41, '货架区', '1-14-2', NULL, NULL, '普通', 1, 2, 14, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(42, '货架区', '1-15-2', NULL, NULL, '普通', 1, 2, 15, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(43, '货架区', '1-01-3', NULL, NULL, '普通', 1, 3, 1, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(44, '货架区', '1-02-3', NULL, NULL, '普通', 1, 3, 2, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(45, '货架区', '1-03-3', NULL, NULL, '普通', 1, 3, 3, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(46, '货架区', '1-04-3', NULL, NULL, '普通', 1, 3, 4, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(47, '货架区', '1-05-3', NULL, NULL, '普通', 1, 3, 5, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(48, '货架区', '1-06-3', NULL, NULL, '普通', 1, 3, 6, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(49, '货架区', '1-07-3', NULL, NULL, '普通', 1, 3, 7, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(50, '货架区', '1-08-3', NULL, NULL, '普通', 1, 3, 8, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(51, '货架区', '1-09-3', NULL, NULL, '普通', 1, 3, 9, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(52, '货架区', '1-10-3', NULL, NULL, '普通', 1, 3, 10, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(53, '货架区', '1-11-3', NULL, NULL, '普通', 1, 3, 11, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(54, '货架区', '1-12-3', NULL, NULL, '普通', 1, 3, 12, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(55, '货架区', '1-13-3', NULL, NULL, '普通', 1, 3, 13, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(56, '货架区', '1-14-3', NULL, NULL, '普通', 1, 3, 14, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(57, '货架区', '1-15-3', NULL, NULL, '普通', 1, 3, 15, 1, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(58, '货架区', '2-01-1', NULL, NULL, '普通', 1, 1, 1, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(59, '货架区', '2-02-1', NULL, NULL, '普通', 1, 1, 2, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(60, '货架区', '2-03-1', NULL, NULL, '普通', 1, 1, 3, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(61, '货架区', '2-04-1', NULL, NULL, '普通', 1, 1, 4, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(62, '货架区', '2-05-1', NULL, NULL, '普通', 1, 1, 5, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(63, '货架区', '2-06-1', NULL, NULL, '普通', 1, 1, 6, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(64, '货架区', '2-07-1', NULL, NULL, '普通', 1, 1, 7, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(65, '货架区', '2-08-1', NULL, NULL, '普通', 1, 1, 8, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(66, '货架区', '2-09-1', NULL, NULL, '普通', 1, 1, 9, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(67, '货架区', '2-10-1', NULL, NULL, '普通', 1, 1, 10, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(68, '货架区', '2-11-1', NULL, NULL, '普通', 1, 1, 11, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(69, '货架区', '2-12-1', NULL, NULL, '普通', 1, 1, 12, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(70, '货架区', '2-13-1', NULL, NULL, '普通', 1, 1, 13, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(71, '货架区', '2-14-1', NULL, NULL, '普通', 1, 1, 14, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(72, '货架区', '2-15-1', NULL, NULL, '普通', 1, 1, 15, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(73, '货架区', '2-01-2', NULL, NULL, '普通', 1, 2, 1, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(74, '货架区', '2-02-2', NULL, NULL, '普通', 1, 2, 2, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(75, '货架区', '2-03-2', NULL, NULL, '普通', 1, 2, 3, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(76, '货架区', '2-04-2', NULL, NULL, '普通', 1, 2, 4, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(77, '货架区', '2-05-2', NULL, NULL, '普通', 1, 2, 5, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(78, '货架区', '2-06-2', NULL, NULL, '普通', 1, 2, 6, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(79, '货架区', '2-07-2', NULL, NULL, '普通', 1, 2, 7, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(80, '货架区', '2-08-2', NULL, NULL, '普通', 1, 2, 8, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(81, '货架区', '2-09-2', NULL, NULL, '普通', 1, 2, 9, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(82, '货架区', '2-10-2', NULL, NULL, '普通', 1, 2, 10, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(83, '货架区', '2-11-2', NULL, NULL, '普通', 1, 2, 11, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(84, '货架区', '2-12-2', NULL, NULL, '普通', 1, 2, 12, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(85, '货架区', '2-13-2', NULL, NULL, '普通', 1, 2, 13, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(86, '货架区', '2-14-2', NULL, NULL, '普通', 1, 2, 14, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(87, '货架区', '2-15-2', NULL, NULL, '普通', 1, 2, 15, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(88, '货架区', '2-01-3', NULL, NULL, '普通', 1, 3, 1, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(89, '货架区', '2-02-3', NULL, NULL, '普通', 1, 3, 2, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(90, '货架区', '2-03-3', NULL, NULL, '普通', 1, 3, 3, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(91, '货架区', '2-04-3', NULL, NULL, '普通', 1, 3, 4, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(92, '货架区', '2-05-3', NULL, NULL, '普通', 1, 3, 5, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(93, '货架区', '2-06-3', NULL, NULL, '普通', 1, 3, 6, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(94, '货架区', '2-07-3', NULL, NULL, '普通', 1, 3, 7, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(95, '货架区', '2-08-3', NULL, NULL, '普通', 1, 3, 8, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(96, '货架区', '2-09-3', NULL, NULL, '普通', 1, 3, 9, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(97, '货架区', '2-10-3', NULL, NULL, '普通', 1, 3, 10, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(98, '货架区', '2-11-3', NULL, NULL, '普通', 1, 3, 11, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(99, '货架区', '2-12-3', NULL, NULL, '普通', 1, 3, 12, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(100, '货架区', '2-13-3', NULL, NULL, '普通', 1, 3, 13, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(101, '货架区', '2-14-3', NULL, NULL, '普通', 1, 3, 14, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(102, '货架区', '2-15-3', NULL, NULL, '普通', 1, 3, 15, 2, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(103, '货架区', '3-01-1', NULL, NULL, '普通', 1, 1, 1, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(104, '货架区', '3-02-1', NULL, NULL, '普通', 1, 1, 2, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(105, '货架区', '3-03-1', NULL, NULL, '普通', 1, 1, 3, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(106, '货架区', '3-04-1', NULL, NULL, '普通', 1, 1, 4, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(107, '货架区', '3-05-1', NULL, NULL, '普通', 1, 1, 5, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(108, '货架区', '3-06-1', NULL, NULL, '普通', 1, 1, 6, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(109, '货架区', '3-07-1', NULL, NULL, '普通', 1, 1, 7, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(110, '货架区', '3-08-1', NULL, NULL, '普通', 1, 1, 8, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(111, '货架区', '3-09-1', NULL, NULL, '普通', 1, 1, 9, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(112, '货架区', '3-10-1', NULL, NULL, '普通', 1, 1, 10, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(113, '货架区', '3-11-1', NULL, NULL, '普通', 1, 1, 11, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(114, '货架区', '3-12-1', NULL, NULL, '普通', 1, 1, 12, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(115, '货架区', '3-13-1', NULL, NULL, '普通', 1, 1, 13, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(116, '货架区', '3-14-1', NULL, NULL, '普通', 1, 1, 14, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(117, '货架区', '3-15-1', NULL, NULL, '普通', 1, 1, 15, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(118, '货架区', '3-01-2', NULL, NULL, '普通', 1, 2, 1, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(119, '货架区', '3-02-2', NULL, NULL, '普通', 1, 2, 2, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(120, '货架区', '3-03-2', NULL, NULL, '普通', 1, 2, 3, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(121, '货架区', '3-04-2', NULL, NULL, '普通', 1, 2, 4, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(122, '货架区', '3-05-2', NULL, NULL, '普通', 1, 2, 5, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(123, '货架区', '3-06-2', NULL, NULL, '普通', 1, 2, 6, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(124, '货架区', '3-07-2', NULL, NULL, '普通', 1, 2, 7, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(125, '货架区', '3-08-2', NULL, NULL, '普通', 1, 2, 8, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(126, '货架区', '3-09-2', NULL, NULL, '普通', 1, 2, 9, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(127, '货架区', '3-10-2', NULL, NULL, '普通', 1, 2, 10, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(128, '货架区', '3-11-2', NULL, NULL, '普通', 1, 2, 11, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(129, '货架区', '3-12-2', NULL, NULL, '普通', 1, 2, 12, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(130, '货架区', '3-13-2', NULL, NULL, '普通', 1, 2, 13, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(131, '货架区', '3-14-2', NULL, NULL, '普通', 1, 2, 14, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(132, '货架区', '3-15-2', NULL, NULL, '普通', 1, 2, 15, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(133, '货架区', '3-01-3', NULL, NULL, '普通', 1, 3, 1, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(134, '货架区', '3-02-3', NULL, NULL, '普通', 1, 3, 2, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(135, '货架区', '3-03-3', NULL, NULL, '普通', 1, 3, 3, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(136, '货架区', '3-04-3', NULL, NULL, '普通', 1, 3, 4, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(137, '货架区', '3-05-3', NULL, NULL, '普通', 1, 3, 5, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(138, '货架区', '3-06-3', NULL, NULL, '普通', 1, 3, 6, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(139, '货架区', '3-07-3', NULL, NULL, '普通', 1, 3, 7, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(140, '货架区', '3-08-3', NULL, NULL, '普通', 1, 3, 8, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(141, '货架区', '3-09-3', NULL, NULL, '普通', 1, 3, 9, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(142, '货架区', '3-10-3', NULL, NULL, '普通', 1, 3, 10, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(143, '货架区', '3-11-3', NULL, NULL, '普通', 1, 3, 11, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(144, '货架区', '3-12-3', NULL, NULL, '普通', 1, 3, 12, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(145, '货架区', '3-13-3', NULL, NULL, '普通', 1, 3, 13, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(146, '货架区', '3-14-3', NULL, NULL, '普通', 1, 3, 14, 3, 'RACK', '备品备件间');
INSERT INTO spare_parts.wms_location
(id, area, code, device_no, remark, `type`, enabled, level_no, position_no, rack_no, storage_mode, warehouse_name)
VALUES(147, '货架区', '3-15-3', NULL, NULL, '普通', 1, 3, 15, 3, 'RACK', '备品备件间');
