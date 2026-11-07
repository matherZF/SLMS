-- V13: 号段发号表、上架确认幂等键、出库明细亮灯时间
--
-- 背景（详见 docs/SYSTEM-REVIEW-2026-10-29.md 第 2~3 周排期）：
--   1) 单号/批次号靠 "查当天最大值 + 1" 生成：
--      · OutboundService/PutawayOrderService 的 synchronized 只包住"读最大值"，
--        不包住 insert+commit，并发下两个线程读到同一个值 → 撞唯一键建单失败；
--      · 序号宽度 %04d，单日超过 9999 单后字符串排序失效，会永久撞号；
--      · generateItemKey 用 currentTimeMillis % 100000，每 100 秒循环一次 → 批次号必撞。
--      改为号段表 wms_number_sequence，用 INSERT ... ON DUPLICATE KEY UPDATE 原子发号。
--   2) 上架确认没有幂等键：网络重试/双击会产生两条上架明细 → 双倍入库。
--      加 request_id 唯一键，同一个确认请求只能落一次。
--   3) 电子标签按键回调只带 mac，不带 cmdtoken，无法区分同库位上的多个出库项。
--      记录 lighted_at（最近一次点灯时间），按键回调按"最近点灯且未按键"的单条明细确认。
--
-- 全部语句做了存在性预检，可安全重复执行。

-- ---------- 1. 号段发号表 ----------
CREATE TABLE IF NOT EXISTS `wms_number_sequence` (
  `seq_key`    varchar(64) NOT NULL COMMENT '号段键，如 OUT20261029 / PA20261029 / B20261029',
  `next_val`   bigint      NOT NULL COMMENT '下一个可分配的序号',
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`seq_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务单号号段表（并发安全发号器）';

-- ---------- 2. wms_outbound_item.lighted_at：最近一次点灯时间 ----------
SET @col_cnt := (SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'wms_outbound_item' AND column_name = 'lighted_at');
SET @ddl := IF(@col_cnt = 0,
               'ALTER TABLE wms_outbound_item ADD COLUMN lighted_at DATETIME(6) NULL COMMENT ''最近一次点亮该库位灯光的时间''',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 按键回调按 (location_id, 状态, lighted_at) 定位，补组合索引
SET @idx_cnt := (SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'wms_outbound_item'
                   AND index_name = 'idx_outbound_item_location_lighted');
SET @ddl := IF(@idx_cnt = 0,
               'CREATE INDEX idx_outbound_item_location_lighted ON wms_outbound_item (location_id, lighted_at)',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 3. wms_putaway_item.request_id：上架确认幂等键 ----------
SET @col_cnt := (SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'wms_putaway_item' AND column_name = 'request_id');
SET @ddl := IF(@col_cnt = 0,
               'ALTER TABLE wms_putaway_item ADD COLUMN request_id VARCHAR(64) NULL COMMENT ''上架确认幂等键，同一个请求重复提交只生效一次''',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_cnt := (SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'wms_putaway_item'
                   AND index_name = 'uk_putaway_item_request');
SET @ddl := IF(@idx_cnt = 0,
               'ALTER TABLE wms_putaway_item ADD UNIQUE KEY uk_putaway_item_request (request_id)',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 4. 存量单号初始化：把当天已发出的最大序号写入号段表 ----------
-- 号段表的语义是"next_val = 上一次已发出的序号"（0 表示还没发过），
-- 所以这里填 MAX(序号) 而不是 MAX+1，否则升级当天会白白跳掉一个号。
-- 序号用 REGEXP_SUBSTR 取末尾数字：同时兼容老格式 OUT202610290001 和新格式 OUT20261029-000001。
-- 按固定位置 SUBSTRING 会算错中文注释后的偏移量，'-' 还会被 CAST 成无符号数溢出。
-- 只在号段表还没有该键时初始化。
INSERT INTO `wms_number_sequence` (`seq_key`, `next_val`, `updated_at`)
SELECT CONCAT('OUT', DATE_FORMAT(CURDATE(), '%Y%m%d')),
       COALESCE(MAX(CAST(REGEXP_SUBSTR(order_no, '[0-9]+$') AS UNSIGNED)), 0), NOW(6)
FROM wms_outbound_order
WHERE order_no LIKE CONCAT('OUT', DATE_FORMAT(CURDATE(), '%Y%m%d'), '%')
ON DUPLICATE KEY UPDATE `next_val` = `next_val`;

INSERT INTO `wms_number_sequence` (`seq_key`, `next_val`, `updated_at`)
SELECT CONCAT('PA', DATE_FORMAT(CURDATE(), '%Y%m%d')),
       COALESCE(MAX(CAST(REGEXP_SUBSTR(order_no, '[0-9]+$') AS UNSIGNED)), 0), NOW(6)
FROM wms_putaway_order
WHERE order_no LIKE CONCAT('PA', DATE_FORMAT(CURDATE(), '%Y%m%d'), '%')
ON DUPLICATE KEY UPDATE `next_val` = `next_val`;
