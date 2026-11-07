-- ============================================================
-- 预警功能优化：用户维度的已读/忽略 + 处理人记录
--
-- 变更说明：
--   1. 新增 wms_alert_user_state 表：每个用户独立的已读/忽略状态
--      → 消息提醒不再互相影响，忽略也是各用户独立
--   2. 主表 wms_spare_part_alert 删除 read_flag（迁移到用户维度）
--      新增 handler_id / handler_name / handled_at（记录处理人）
--   3. 已处理(ACK) 仍是全局状态：有人处理了，所有人看到的都是已处理
--   4. 原来的 IGNORED 状态从主表移除，改由用户维度存储
-- ============================================================

-- 1. 先把主表里已有的 read_flag 迁移出来（迁移时还没建表，V28 之前的历史数据里 read_flag 是全局的）
--    由于之前 read_flag 是全局的，迁移到用户维度时默认所有人未读（用户自己会重新标记）
--    这里只处理 status=IGNORED 的旧数据：把它们改回 OPEN，让用户自己重新选择忽略

UPDATE wms_spare_part_alert SET status = 'OPEN' WHERE status = 'IGNORED';

-- 2. 新增处理人字段
ALTER TABLE wms_spare_part_alert
    ADD COLUMN handler_id   BIGINT       NULL COMMENT '处理人用户ID',
    ADD COLUMN handler_name VARCHAR(128) NULL COMMENT '处理人姓名/用户名',
    ADD COLUMN handled_at   DATETIME     NULL COMMENT '处理时间（ACK 时写入）';

-- 3. 删除旧的 read_flag（由新表替代）
ALTER TABLE wms_spare_part_alert DROP COLUMN read_flag;

-- 4. 新增用户维度状态表
CREATE TABLE wms_alert_user_state (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    alert_id   BIGINT   NOT NULL COMMENT '预警ID',
    user_id    BIGINT   NOT NULL COMMENT '用户ID',
    read_flag  BIT      NOT NULL DEFAULT 0 COMMENT '该用户是否已读',
    ignored    BIT      NOT NULL DEFAULT 0 COMMENT '该用户是否已忽略（只影响自己视图）',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_alert_user (alert_id, user_id),
    KEY idx_user (user_id, read_flag, ignored)
) COMMENT='预警用户维度状态（已读/忽略各用户独立）';
