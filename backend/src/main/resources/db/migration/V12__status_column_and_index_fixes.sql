-- V12: 修复状态列枚举值域、补齐索引与唯一约束、统一排序规则
--
-- 背景（详见 docs/SYSTEM-REVIEW-2026-10-29.md）：
--   1) wms_stocktake_order.status 在 V1 被建成 enum('COUNTING','DONE','CANCELLED')，
--      而 StocktakeStatus 还有 PENDING_REVIEW，导致盘点提交差异时 MySQL 严格模式下报
--      ERROR 1265，整条差异复核链路不可用。
--   2) wms_putaway_order.status 同样是 ENUM，当前取值刚好吻合，这里做类型守护。
--   3) 索引/唯一约束缺失、wms_es_tag 排序规则与其他表不一致（JOIN 报 ERROR 1267）。
--
-- 重要：status 列必须保持 MySQL 原生 ENUM，不能改 VARCHAR。
--   Hibernate 6 对 @Enumerated(EnumType.STRING) 期望的列类型码就是 Types#ENUM，
--   项目 ddl-auto=validate，改成 VARCHAR 会导致启动即失败
--   （实测报 wrong column type: found [varchar], but expecting [enum]）。
--   因此这里只扩展 ENUM 的值域，不改变类型。
--
-- 本脚本全部语句做了存在性预检，可安全重复执行：目标状态已达成则跳过（DO 0）。

-- ---------- 1. wms_stocktake_order.status: 扩展 ENUM 值域，补 PENDING_REVIEW ----------
-- 条件：不是 ENUM 类型，或值域里没有 PENDING_REVIEW —— 两种情况都重建为完整值域。
SET @cur_type := (SELECT column_type FROM information_schema.columns
                  WHERE table_schema = DATABASE() AND table_name = 'wms_stocktake_order' AND column_name = 'status');
SET @ddl := IF(@cur_type NOT LIKE 'enum%' OR @cur_type NOT LIKE '%PENDING_REVIEW%',
               'ALTER TABLE wms_stocktake_order MODIFY COLUMN `status` ENUM(''COUNTING'',''PENDING_REVIEW'',''DONE'',''CANCELLED'') NOT NULL',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2. wms_putaway_order.status: 类型守护（必须是 ENUM） ----------
SET @cur_type := (SELECT column_type FROM information_schema.columns
                  WHERE table_schema = DATABASE() AND table_name = 'wms_putaway_order' AND column_name = 'status');
SET @ddl := IF(@cur_type NOT LIKE 'enum%',
               'ALTER TABLE wms_putaway_order MODIFY COLUMN `status` ENUM(''PENDING'',''DONE'') NOT NULL',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 3. 统一 wms_es_tag 排序规则（否则与 utf8mb4_0900_ai_ci 的表 JOIN 报 1267） ----------
SET @cur_coll := (SELECT table_collation FROM information_schema.tables
                  WHERE table_schema = DATABASE() AND table_name = 'wms_es_tag');
SET @ddl := IF(@cur_coll IS NOT NULL AND @cur_coll <> 'utf8mb4_0900_ai_ci',
               'ALTER TABLE wms_es_tag CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 4. wms_location.device_no 唯一键（一个 MAC 只能绑一个库位） ----------
-- 若 @dup_cnt > 0 说明存量已有重复 MAC，需先人工清理后单独执行；此处只跳过而不阻断迁移。
SET @dup_cnt := (SELECT COUNT(*) FROM (SELECT device_no FROM wms_location
                                       WHERE device_no IS NOT NULL AND device_no <> ''
                                       GROUP BY device_no HAVING COUNT(*) > 1) d);
SET @idx_cnt := (SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'wms_location'
                   AND index_name = 'uk_location_device_no');
SET @ddl := IF(@dup_cnt = 0 AND @idx_cnt = 0,
               'ALTER TABLE wms_location ADD UNIQUE KEY uk_location_device_no (device_no)',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 5. 唯一键加不上时（存量重复），退化为普通索引保证查询性能 ----------
-- 唯一键本身即可服务该查询，因此仅在不具备唯一键时才额外建普通索引，避免冗余索引。
SET @uk_cnt := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'wms_location'
                  AND index_name = 'uk_location_device_no');
SET @idx_cnt := (SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'wms_location'
                   AND index_name = 'idx_location_device_no');
SET @need_idx := IF(@uk_cnt = 0 AND @idx_cnt = 0, 1, 0);
SET @ddl := IF(@need_idx = 1,
               'CREATE INDEX idx_location_device_no ON wms_location (device_no)',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 6. wms_es_tag.location_id 唯一键（一个库位只能绑一个标签） ----------
SET @dup_cnt := (SELECT COUNT(*) FROM (SELECT location_id FROM wms_es_tag
                                       WHERE location_id IS NOT NULL
                                       GROUP BY location_id HAVING COUNT(*) > 1) d);
SET @idx_cnt := (SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'wms_es_tag'
                   AND index_name = 'uk_es_tag_location');
SET @ddl := IF(@dup_cnt = 0 AND @idx_cnt = 0,
               'ALTER TABLE wms_es_tag DROP INDEX idx_es_tag_location, ADD UNIQUE KEY uk_es_tag_location (location_id)',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 7. 出库单状态索引（列表筛选、首页统计、pendingList 高频使用） ----------
SET @idx_cnt := (SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'wms_outbound_order'
                   AND index_name = 'idx_outbound_order_status');
SET @ddl := IF(@idx_cnt = 0,
               'CREATE INDEX idx_outbound_order_status ON wms_outbound_order (status, id)',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 8. 待审批业务查询索引（退库/报废/盘点每次提交都会查） ----------
SET @idx_cnt := (SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'wms_pending_business_action'
                   AND index_name = 'idx_pending_action_lookup');
SET @ddl := IF(@idx_cnt = 0,
               'CREATE INDEX idx_pending_action_lookup ON wms_pending_business_action (business_type, source_id, status)',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 9. 使用台账按单据/设备查询索引 ----------
SET @idx_cnt := (SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'wms_spare_part_usage'
                   AND index_name = 'idx_usage_outbound_order');
SET @ddl := IF(@idx_cnt = 0,
               'CREATE INDEX idx_usage_outbound_order ON wms_spare_part_usage (outbound_order_id)',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_cnt := (SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'wms_spare_part_usage'
                   AND index_name = 'idx_usage_equipment');
SET @ddl := IF(@idx_cnt = 0,
               'CREATE INDEX idx_usage_equipment ON wms_spare_part_usage (equipment_id)',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 10. 清理 V5 里与外键自动索引完全重复的冗余索引 ----------
SET @idx_cnt := (SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'wms_outbound_item'
                   AND index_name = 'idx_outbound_item_location');
SET @ddl := IF(@idx_cnt > 0,
               'ALTER TABLE wms_outbound_item DROP INDEX idx_outbound_item_location',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 11. 时间列精度对齐实体 Instant（微秒），避免毫秒被静默截断 ----------
-- 当前按钮回调密集时无法区分先后，会影响按键时序审计。
SET @cur_type := (SELECT column_type FROM information_schema.columns
                  WHERE table_schema = DATABASE() AND table_name = 'wms_outbound_item' AND column_name = 'button_pressed_at');
SET @ddl := IF(@cur_type IS NOT NULL AND @cur_type NOT LIKE '%(6)',
               'ALTER TABLE wms_outbound_item MODIFY COLUMN button_pressed_at DATETIME(6) NULL, MODIFY COLUMN light_confirmed_at DATETIME(6) NULL',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @cur_type := (SELECT column_type FROM information_schema.columns
                  WHERE table_schema = DATABASE() AND table_name = 'wms_es_tag' AND column_name = 'last_seen_at');
SET @ddl := IF(@cur_type IS NOT NULL AND @cur_type NOT LIKE '%(6)',
               'ALTER TABLE wms_es_tag MODIFY COLUMN last_seen_at DATETIME(6) NULL',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
