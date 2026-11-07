-- ============================================================
-- [历史文件，请勿用于当前环境初始化]
-- 当前结构由 db/migration/V1__baseline_schema.sql 和后续 Flyway 迁移管理。
-- 本文件包含 DROP TABLE，仅用于追溯早期设计，禁止在已有数据库执行。
-- WMS 仓储管理系统 - 早期 MySQL 建库建表脚本
-- 数据库: wms
-- 字符集: utf8mb4
-- 说明:
--   1. 本脚本用于手动初始化数据库结构（DDL）。
--   2. 应用启动时 JPA (ddl-auto=update) 会自动校验/更新表结构。
--   3. 初始种子数据（用户、商品、库位、批次、库存等）由
--      DataInitializer 在应用首次启动时自动写入，无需在此脚本中插入。
-- ============================================================

-- 创建数据库（如不存在）
CREATE DATABASE IF NOT EXISTS `wms`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `wms`;

-- ------------------------------------------------------------
-- 1. 用户表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `wms_user`;
CREATE TABLE `wms_user` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username`     VARCHAR(64)  NOT NULL COMMENT '登录账号',
    `password`     VARCHAR(128) NOT NULL COMMENT '密码（BCrypt加密）',
    `display_name` VARCHAR(64)  DEFAULT NULL COMMENT '显示名称',
    `role`         VARCHAR(16)  NOT NULL COMMENT '角色：ADMIN / APPROVER / WAREHOUSE / VIEWER',
    `enabled`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    `created_at`   DATETIME     DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ------------------------------------------------------------
-- 2. 商品表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `wms_product`;
CREATE TABLE `wms_product` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `sku`        VARCHAR(64)  NOT NULL COMMENT 'SKU编码',
    `name`       VARCHAR(128) NOT NULL COMMENT '商品名称',
    `spec`                    VARCHAR(255) DEFAULT NULL COMMENT '规格',
    `unit`                    VARCHAR(32)  DEFAULT NULL COMMENT '单位',
    `category`                VARCHAR(64)  DEFAULT NULL COMMENT '备件分类',
    `brand`                   VARCHAR(64)  DEFAULT NULL COMMENT '品牌',
    `model`                   VARCHAR(128) DEFAULT NULL COMMENT '型号',
    `drawing_no`              VARCHAR(64)  DEFAULT NULL COMMENT '图号',
    `manufacturer`            VARCHAR(128) DEFAULT NULL COMMENT '生产厂家',
    `criticality`             VARCHAR(16)  NOT NULL DEFAULT 'NORMAL' COMMENT '关键程度',
    `vulnerable`              BIT          NOT NULL DEFAULT b'0' COMMENT '是否易损件',
    `batch_managed`           BIT          NOT NULL DEFAULT b'1' COMMENT '是否批次管理',
    `serial_managed`          BIT          NOT NULL DEFAULT b'0' COMMENT '是否序列号管理',
    `default_shelf_life_days` INT          DEFAULT NULL COMMENT '默认保质期（天）',
    `min_stock`               INT          NOT NULL DEFAULT 0 COMMENT '最低库存',
    `max_stock`               INT          DEFAULT NULL COMMENT '最高库存',
    `safety_stock`            INT          NOT NULL DEFAULT 0 COMMENT '安全库存',
    `procurement_lead_days`   INT          NOT NULL DEFAULT 0 COMMENT '采购提前期（天）',
    `life_metric`             VARCHAR(16)  NOT NULL DEFAULT 'NONE' COMMENT '寿命计量：NONE/HOURS/TASKS/DAYS',
    `rated_life_value`        DECIMAL(18,2) DEFAULT NULL COMMENT '额定寿命值',
    `life_warning_percent`    INT          NOT NULL DEFAULT 80 COMMENT '寿命预警阈值（百分比）',
    `enabled`                 BIT          NOT NULL DEFAULT b'1' COMMENT '是否启用',
    `remark`                  VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `created_at`              DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updated_at`              DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_sku` (`sku`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';

-- ------------------------------------------------------------
-- 3. 库位表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `wms_location`;
CREATE TABLE `wms_location` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `code`       VARCHAR(64)  NOT NULL COMMENT '库位编码',
    `area`       VARCHAR(64)  DEFAULT NULL COMMENT '所属区域',
    `type`       VARCHAR(64)  DEFAULT NULL COMMENT '库位类型',
    `warehouse_name` VARCHAR(128) DEFAULT NULL COMMENT '仓库名称',
    `storage_mode`   VARCHAR(16)  DEFAULT NULL COMMENT '存储方式：RACK/FLOOR',
    `rack_no`        INT          DEFAULT NULL COMMENT '货架排号',
    `level_no`       INT          DEFAULT NULL COMMENT '货架层号',
    `position_no`    INT          DEFAULT NULL COMMENT '层内位置号',
    `enabled`        BIT          NOT NULL DEFAULT b'1' COMMENT '是否启用',
    `device_no`  VARCHAR(64)  DEFAULT NULL COMMENT '绑定灯光设备编号',
    `remark`     VARCHAR(255) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_location_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库位表';

-- ------------------------------------------------------------
-- 4. 批次表
--    批次由 item_key、生产日期、保质期、生产厂商等信息共同构成
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `wms_batch`;
CREATE TABLE `wms_batch` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `item_key`         VARCHAR(64)  NOT NULL COMMENT '批次号（唯一）',
    `product_id`       BIGINT       NOT NULL COMMENT '关联商品ID',
    `sku`              VARCHAR(64)  DEFAULT NULL COMMENT 'SKU编码（冗余）',
    `product_name`     VARCHAR(128) DEFAULT NULL COMMENT '商品名称（冗余）',
    `production_date`  DATE         DEFAULT NULL COMMENT '生产日期',
    `shelf_life_days`  INT          DEFAULT NULL COMMENT '保质期（天）',
    `manufacturer`     VARCHAR(128) DEFAULT NULL COMMENT '生产厂商',
    `created_at`       DATETIME     DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_batch_item_key` (`item_key`),
    KEY `idx_batch_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='批次表';

-- ------------------------------------------------------------
-- 5. 入库单表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `wms_putaway_order`;
CREATE TABLE `wms_putaway_order` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_no`     VARCHAR(64)  NOT NULL COMMENT '入库单号',
    `product_id`   BIGINT       NOT NULL COMMENT '关联商品ID',
    `batch_id`     BIGINT       DEFAULT NULL COMMENT '关联批次ID',
    `plan_qty`     INT          NOT NULL COMMENT '计划入库数量',
    `put_qty`      INT          NOT NULL DEFAULT 0 COMMENT '已上架数量',
    `status`       VARCHAR(16)  NOT NULL COMMENT '状态：PENDING / DONE',
    `created_at`   DATETIME     DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_putaway_order_no` (`order_no`),
    KEY `idx_putaway_product` (`product_id`),
    KEY `idx_putaway_batch` (`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='入库单表';

-- ------------------------------------------------------------
-- 6. 入库明细表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `wms_putaway_item`;
CREATE TABLE `wms_putaway_item` (
    `id`                BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `putaway_order_id`  BIGINT   NOT NULL COMMENT '入库单ID',
    `location_id`       BIGINT   NOT NULL COMMENT '库位ID',
    `qty`               INT      NOT NULL COMMENT '上架数量',
    `created_at`        DATETIME DEFAULT NULL COMMENT '上架时间',
    PRIMARY KEY (`id`),
    KEY `idx_putaway_item_order` (`putaway_order_id`),
    KEY `idx_putaway_item_location` (`location_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='入库明细表';

-- ------------------------------------------------------------
-- 7. 出库单表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `wms_outbound_order`;
CREATE TABLE `wms_outbound_order` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_no`     VARCHAR(64)  NOT NULL COMMENT '出库单号',
    `status`       VARCHAR(32)  NOT NULL COMMENT '状态：PENDING_APPROVAL / APPROVED / PICKING / DONE / REJECTED / CANCELLED',
    `equipment_id` BIGINT       DEFAULT NULL COMMENT '领用设备ID',
    `equipment_code_snapshot` VARCHAR(64) DEFAULT NULL COMMENT '领用时设备编码快照',
    `equipment_name_snapshot` VARCHAR(128) DEFAULT NULL COMMENT '领用时设备名称快照',
    `purpose`      VARCHAR(500) DEFAULT NULL COMMENT '领用用途',
    `applicant`    VARCHAR(64)  DEFAULT NULL COMMENT '申请人',
    `department`   VARCHAR(64)  DEFAULT NULL COMMENT '领用部门',
    `recipient`    VARCHAR(64)  DEFAULT NULL COMMENT '实际领用人',
    `contact_phone` VARCHAR(32) DEFAULT NULL COMMENT '联系电话',
    `work_order_no` VARCHAR(64) DEFAULT NULL COMMENT '维修/工作单号',
    `required_date` DATE        DEFAULT NULL COMMENT '期望领用日期',
    `delivery_location` VARCHAR(128) DEFAULT NULL COMMENT '交付地点',
    `reviewed_by`  VARCHAR(64)  DEFAULT NULL COMMENT '审批人',
    `review_opinion` VARCHAR(500) DEFAULT NULL COMMENT '审批意见',
    `reviewed_at`  DATETIME     DEFAULT NULL COMMENT '审批时间',
    `picked_by`    VARCHAR(64)  DEFAULT NULL COMMENT '拣货操作人',
    `picked_at`    DATETIME     DEFAULT NULL COMMENT '开始拣货时间',
    `completed_by` VARCHAR(64)  DEFAULT NULL COMMENT '完成操作人',
    `completed_at` DATETIME     DEFAULT NULL COMMENT '出库完成时间',
    `created_at`   DATETIME     DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_outbound_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='出库单表';

-- ------------------------------------------------------------
-- 8. 出库明细表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `wms_outbound_item`;
CREATE TABLE `wms_outbound_item` (
    `id`                 BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `outbound_order_id`  BIGINT   NOT NULL COMMENT '出库单ID',
    `product_id`         BIGINT   NOT NULL COMMENT '商品ID',
    `product_sku_snapshot`  VARCHAR(64)  DEFAULT NULL COMMENT '领用时备件编码快照',
    `product_name_snapshot` VARCHAR(128) DEFAULT NULL COMMENT '领用时备件名称快照',
    `product_spec_snapshot` VARCHAR(255) DEFAULT NULL COMMENT '领用时规格快照',
    `product_unit_snapshot` VARCHAR(32)  DEFAULT NULL COMMENT '领用时单位快照',
    `location_id`        BIGINT   DEFAULT NULL COMMENT '拣货库位ID',
    `batch_id`           BIGINT   DEFAULT NULL COMMENT '拣货批次ID',
    `requested_qty`      INT      NOT NULL COMMENT '需求数量',
    `picked_qty`         INT      NOT NULL DEFAULT 0 COMMENT '已拣数量',
    `returned_qty`       INT      NOT NULL DEFAULT 0 COMMENT '累计退库数量',
    `scan_verified`      BIT(1)   NOT NULL DEFAULT 0 COMMENT '是否完成双码核验',
    `scan_verified_at`   DATETIME DEFAULT NULL COMMENT '双码核验时间',
    `created_at`         DATETIME DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_outbound_item_order` (`outbound_order_id`),
    KEY `idx_outbound_item_product` (`product_id`),
    KEY `idx_outbound_item_location` (`location_id`),
    KEY `idx_outbound_item_batch` (`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='出库明细表';

-- ------------------------------------------------------------
-- 9. 统一操作审计日志
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `wms_approval_record`;
CREATE TABLE `wms_approval_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `business_type` VARCHAR(32) NOT NULL COMMENT '业务类型',
    `business_id` BIGINT NOT NULL COMMENT '业务主键',
    `business_no` VARCHAR(64) NOT NULL COMMENT '业务单号',
    `round_no` INT NOT NULL COMMENT '审批轮次',
    `applicant` VARCHAR(64) NOT NULL COMMENT '提交人',
    `approver` VARCHAR(64) DEFAULT NULL COMMENT '审批人',
    `status` VARCHAR(16) NOT NULL COMMENT 'PENDING/APPROVED/REJECTED/CANCELLED',
    `opinion` VARCHAR(500) DEFAULT NULL COMMENT '审批意见',
    `submitted_at` DATETIME NOT NULL COMMENT '提交时间',
    `handled_at` DATETIME DEFAULT NULL COMMENT '处理时间',
    `created_at` DATETIME DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_approval_business` (`business_type`,`business_id`),
    KEY `idx_approval_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一审批记录';

DROP TABLE IF EXISTS `wms_operation_audit_log`;
CREATE TABLE `wms_operation_audit_log` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `business_type` VARCHAR(32)   NOT NULL COMMENT '业务类型',
    `business_id`   BIGINT        NOT NULL COMMENT '业务主键',
    `business_no`   VARCHAR(64)   DEFAULT NULL COMMENT '业务单号',
    `action`        VARCHAR(32)   NOT NULL COMMENT '操作动作',
    `from_status`   VARCHAR(32)   DEFAULT NULL COMMENT '原状态',
    `to_status`     VARCHAR(32)   DEFAULT NULL COMMENT '新状态',
    `operator`      VARCHAR(64)   DEFAULT NULL COMMENT '操作人',
    `detail`        VARCHAR(1000) DEFAULT NULL COMMENT '操作说明',
    `created_at`    DATETIME      DEFAULT NULL COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_audit_business` (`business_type`, `business_id`),
    KEY `idx_audit_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一操作审计日志';

CREATE TABLE `wms_data_dictionary` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `dict_type` VARCHAR(32) NOT NULL COMMENT '字典类型',
    `item_code` VARCHAR(64) NOT NULL COMMENT '字典编码',
    `item_label` VARCHAR(128) NOT NULL COMMENT '显示名称',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `enabled` BIT NOT NULL DEFAULT b'1' COMMENT '是否启用',
    `remark` VARCHAR(255) DEFAULT NULL,
    `created_at` DATETIME DEFAULT NULL,
    `updated_at` DATETIME DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dict_type_code` (`dict_type`,`item_code`),
    KEY `idx_dict_type_enabled` (`dict_type`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据字典';

-- ------------------------------------------------------------
-- 9. 备件使用台账
--    确认出库即开始使用，一条出库明细只建立一条使用记录
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `wms_spare_part_usage`;
CREATE TABLE `wms_spare_part_usage` (
    `id`                     BIGINT        NOT NULL AUTO_INCREMENT,
    `usage_no`               VARCHAR(64)   NOT NULL COMMENT '使用记录编号',
    `outbound_order_id`       BIGINT        NOT NULL,
    `outbound_order_no`       VARCHAR(64)   NOT NULL,
    `outbound_item_id`        BIGINT        NOT NULL,
    `product_id`              BIGINT        NOT NULL,
    `sku`                     VARCHAR(64)   NOT NULL,
    `product_name`            VARCHAR(128)  NOT NULL,
    `spec`                    VARCHAR(255)  DEFAULT NULL,
    `unit`                    VARCHAR(32)   DEFAULT NULL,
    `batch_id`                BIGINT        DEFAULT NULL,
    `batch_no`                VARCHAR(64)   DEFAULT NULL,
    `qty`                     INT           NOT NULL,
    `active_qty`              INT           NOT NULL DEFAULT 0 COMMENT '当前在用数量',
    `returned_qty`            INT           NOT NULL DEFAULT 0 COMMENT '已退回数量',
    `equipment_id`            BIGINT        DEFAULT NULL,
    `equipment_code`          VARCHAR(64)   DEFAULT NULL,
    `equipment_name`          VARCHAR(128)  DEFAULT NULL,
    `life_metric`             VARCHAR(16)   NOT NULL,
    `rated_life_value`        DECIMAL(18,2) DEFAULT NULL,
    `warning_percent`         INT           NOT NULL DEFAULT 80,
    `baseline_running_hours`  DECIMAL(18,2) DEFAULT NULL,
    `baseline_task_count`     BIGINT        DEFAULT NULL,
    `current_running_hours`   DECIMAL(18,2) DEFAULT NULL,
    `current_task_count`      BIGINT        DEFAULT NULL,
    `status`                  VARCHAR(24)   NOT NULL,
    `baseline_status`         VARCHAR(24)   NOT NULL,
    `data_error`              VARCHAR(500)  DEFAULT NULL,
    `started_at`              DATETIME(6)   NOT NULL,
    `last_synced_at`          DATETIME(6)   DEFAULT NULL,
    `ended_at`                DATETIME(6)   DEFAULT NULL COMMENT '结束跟踪时间',
    `end_reason`              VARCHAR(500)  DEFAULT NULL COMMENT '结束原因',
    `created_at`              DATETIME(6)   DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_usage_no` (`usage_no`),
    UNIQUE KEY `uk_usage_outbound_item` (`outbound_item_id`),
    KEY `idx_usage_product` (`product_id`),
    KEY `idx_usage_equipment` (`equipment_code`),
    KEY `idx_usage_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='备件使用台账';

DROP TABLE IF EXISTS `wms_spare_part_usage_snapshot`;
CREATE TABLE `wms_spare_part_usage_snapshot` (
    `id`                   BIGINT        NOT NULL AUTO_INCREMENT,
    `usage_id`             BIGINT        NOT NULL COMMENT '备件使用台账ID',
    `source`               VARCHAR(16)   NOT NULL COMMENT 'OUTBOUND/MANUAL/SCHEDULED',
    `running_hours`        DECIMAL(18,2) DEFAULT NULL,
    `task_count`           BIGINT        DEFAULT NULL,
    `used_life_value`      DECIMAL(18,2) DEFAULT NULL,
    `remaining_life_value` DECIMAL(18,2) DEFAULT NULL,
    `used_percent`         INT           DEFAULT NULL,
    `status`               VARCHAR(24)   NOT NULL,
    `collected_at`         DATETIME(6)   NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_usage_snapshot_usage_time` (`usage_id`, `collected_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='备件寿命数据同步快照';

-- ------------------------------------------------------------
-- 10. 库存表
--    唯一键 (product_id, location_id, batch_id)：同一商品在同一库位的同一批次只有一条库存记录
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `wms_inventory`;
CREATE TABLE `wms_inventory` (
    `id`           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `product_id`   BIGINT   NOT NULL COMMENT '商品ID',
    `location_id`  BIGINT   NOT NULL COMMENT '库位ID',
    `batch_id`     BIGINT   DEFAULT NULL COMMENT '批次ID',
    `qty`          INT      NOT NULL DEFAULT 0 COMMENT '库存数量',
    `updated_at`   DATETIME DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inventory_prod_loc_batch` (`product_id`, `location_id`, `batch_id`),
    KEY `idx_inventory_location` (`location_id`),
    KEY `idx_inventory_batch` (`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存表';

-- ------------------------------------------------------------
-- 11. 库存变动日志表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `wms_inventory_log`;
CREATE TABLE `wms_inventory_log` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `product_id`       BIGINT       NOT NULL COMMENT '商品ID',
    `product_name`     VARCHAR(128) DEFAULT NULL COMMENT '商品名称（冗余）',
    `sku`              VARCHAR(64)  DEFAULT NULL COMMENT 'SKU编码（冗余）',
    `location_id`      BIGINT       DEFAULT NULL COMMENT '库位ID',
    `location_code`    VARCHAR(64)  DEFAULT NULL COMMENT '库位编码（冗余）',
    `change_type`      VARCHAR(16)  NOT NULL COMMENT '变动类型：INBOUND / OUTBOUND',
    `change_qty`       INT          NOT NULL COMMENT '变动数量',
    `before_qty`       INT          NOT NULL COMMENT '变动前库存',
    `after_qty`        INT          NOT NULL COMMENT '变动后库存',
    `ref_type`         VARCHAR(32)  DEFAULT NULL COMMENT '关联单据类型：PUTAWAY / OUTBOUND',
    `ref_no`           VARCHAR(64)  DEFAULT NULL COMMENT '关联单号',
    `operator`         VARCHAR(64)  DEFAULT NULL COMMENT '操作人',
    `remark`           VARCHAR(255) DEFAULT NULL COMMENT '备注',
    -- 批次信息
    `item_key`         VARCHAR(64)  DEFAULT NULL COMMENT '批次号',
    `production_date`  DATE         DEFAULT NULL COMMENT '生产日期',
    `shelf_life_days`  INT          DEFAULT NULL COMMENT '保质期（天）',
    `manufacturer`     VARCHAR(128) DEFAULT NULL COMMENT '生产厂商',
    `created_at`       DATETIME     DEFAULT NULL COMMENT '记录时间',
    PRIMARY KEY (`id`),
    KEY `idx_log_product` (`product_id`),
    KEY `idx_log_location` (`location_id`),
    KEY `idx_log_ref_no` (`ref_no`),
    KEY `idx_log_change_type` (`change_type`),
    KEY `idx_log_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存变动日志表';

-- ------------------------------------------------------------
-- 外键约束
-- ------------------------------------------------------------
ALTER TABLE `wms_putaway_order`
    ADD CONSTRAINT `fk_putaway_order_product` FOREIGN KEY (`product_id`) REFERENCES `wms_product` (`id`),
    ADD CONSTRAINT `fk_putaway_order_batch`   FOREIGN KEY (`batch_id`)   REFERENCES `wms_batch` (`id`);

ALTER TABLE `wms_putaway_item`
    ADD CONSTRAINT `fk_putaway_item_order`    FOREIGN KEY (`putaway_order_id`) REFERENCES `wms_putaway_order` (`id`) ON DELETE CASCADE,
    ADD CONSTRAINT `fk_putaway_item_location` FOREIGN KEY (`location_id`)      REFERENCES `wms_location` (`id`);

ALTER TABLE `wms_outbound_item`
    ADD CONSTRAINT `fk_outbound_item_order`    FOREIGN KEY (`outbound_order_id`) REFERENCES `wms_outbound_order` (`id`) ON DELETE CASCADE,
    ADD CONSTRAINT `fk_outbound_item_product`  FOREIGN KEY (`product_id`)        REFERENCES `wms_product` (`id`),
    ADD CONSTRAINT `fk_outbound_item_location` FOREIGN KEY (`location_id`)       REFERENCES `wms_location` (`id`),
    ADD CONSTRAINT `fk_outbound_item_batch`    FOREIGN KEY (`batch_id`)          REFERENCES `wms_batch` (`id`);

ALTER TABLE `wms_inventory`
    ADD CONSTRAINT `fk_inventory_product`  FOREIGN KEY (`product_id`)  REFERENCES `wms_product` (`id`),
    ADD CONSTRAINT `fk_inventory_location` FOREIGN KEY (`location_id`) REFERENCES `wms_location` (`id`),
    ADD CONSTRAINT `fk_inventory_batch`    FOREIGN KEY (`batch_id`)    REFERENCES `wms_batch` (`id`);
