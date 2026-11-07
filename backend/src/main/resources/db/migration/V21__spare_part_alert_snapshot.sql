-- ============================================================
-- 备件预警记录表：每天定时计算一次并落表，页面读表而不是实时算
--
-- 之前 /api/alerts 是打开页面时才算，没人打开就等于没预警。落表之后才有：
--   · 历史可追溯（某天报了什么、当时库存多少）
--   · 去重（同一对象只保留一条，处理后不再反复提醒）
--   · 站内消息（未读计数、已读/已处理/忽略）
--
-- dedup_key 是「预警对象」的唯一标识，不含日期：
-- 同一备件的同一类问题只保留一条记录，处理掉之后不再自动生成新的，
-- 除非情况恶化（MEDIUM 升 HIGH）才重新打开。
-- ============================================================

CREATE TABLE wms_spare_part_alert (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    alert_date            DATE         NOT NULL COMMENT '预警所属日期',
    alert_type            VARCHAR(16)  NOT NULL COMMENT 'STOCK 库存 / EXPIRY 效期 / LIFECYCLE 更换 / FORECAST 预测',
    scope                 VARCHAR(16)  COMMENT 'PRODUCT 备件级 / DEVICE 设备级',
    product_id            BIGINT       COMMENT '备件；效期与更换预警也可能为空',
    sku                   VARCHAR(64),
    product_name          VARCHAR(128),
    equipment_id          BIGINT       COMMENT '设备级预警关联的设备',
    equipment_code        VARCHAR(64),
    equipment_name        VARCHAR(128),
    usage_id              BIGINT       COMMENT '更换预警对应的使用台账',
    inventory_id          BIGINT       COMMENT '效期预警对应的库存记录',
    severity              VARCHAR(16)  NOT NULL COMMENT 'HIGH / MEDIUM',
    stock_qty             INT          COMMENT '当前可用库存',
    threshold_qty         INT          COMMENT '触发阈值：补货点或安全库存',
    safety_stock          INT          COMMENT '备件档案的安全库存，用于区分「该下单」与「已在吃缓冲」',
    daily_demand          DECIMAL(18,4) COMMENT '日耗（件/天）',
    coverage_days         INT          COMMENT '当前库存还能撑几天',
    stockout_date         DATE         COMMENT '预计见底日',
    receive_by_date       DATE         COMMENT '最迟到货日',
    order_by_date         DATE         COMMENT '最晚采购日；无采购周期时为空',
    suggested_qty         INT          COMMENT '建议采购量',
    procurement_lead_days INT          COMMENT '采购周期',
    lead_estimated        BIT          NOT NULL DEFAULT 0 COMMENT '1=采购周期是估算值',
    message               VARCHAR(500) NOT NULL COMMENT '给现场看的一句话提醒',
    status                VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN 待处理 / ACK 已处理 / IGNORED 已忽略',
    read_flag             BIT          NOT NULL DEFAULT 0 COMMENT '站内消息已读标记',
    dedup_key             VARCHAR(200) NOT NULL COMMENT '预警对象唯一键：类型+备件+设备+台账+库存',
    created_at            DATETIME     NOT NULL,
    updated_at            DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_alert_date (alert_date, status),
    KEY idx_dedup (dedup_key),
    KEY idx_unread (status, read_flag)
) COMMENT='备件预警记录（每天定时计算并落表）';
