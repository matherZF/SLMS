-- ============================================================
-- 站内消息「已读」回执：已读后消息不消失，只是不再出红点
--
-- 背景：
--   预警的已读/未读原本就在 wms_alert_user_state.read_flag（用户维度），
--   而审批消息一旦被点开看过，下一步处理完就从任务列表消失，无法再"留在消息里"。
--   所以这里补一张通用的已读回执表：按 (用户, 消息分类, 目标ID) 记录已读，
--   并快照标题/副标题/正文，保证源数据消失后消息仍能在弹框里按已读展示。
--
-- 设计要点：
--   1. 只存回执，不改业务表；未读 = 有源数据且无回执（预警另用 read_flag）
--   2. 快照字段允许为空：源数据还在时以实时数据为准
--   3. read_at 保留 30 天，超过的在写入时顺手清理，表不会无限膨胀
-- ============================================================

CREATE TABLE IF NOT EXISTS wms_notification_read (
  id                BIGINT       AUTO_INCREMENT PRIMARY KEY,
  username          VARCHAR(64)  NOT NULL COMMENT '用户登录名（回执只对本人生效）',
  category          VARCHAR(16)  NOT NULL COMMENT '消息分类：ALERT / APPROVAL / PICKING / STOCKTAKE',
  target_id         BIGINT       NOT NULL COMMENT '目标ID（预警ID / 审批 businessId）',
  severity          VARCHAR(16)  NULL     COMMENT '级别快照：HIGH / MEDIUM / INFO',
  title             VARCHAR(128) NULL     COMMENT '标题快照（源数据消失后用于展示）',
  subtitle          VARCHAR(255) NULL     COMMENT '副标题快照',
  message           VARCHAR(512) NULL     COMMENT '正文快照',
  route             VARCHAR(128) NULL     COMMENT '点击跳转路由快照',
  source_created_at DATETIME     NULL     COMMENT '消息产生时间快照（排序列）',
  read_at           DATETIME     NOT NULL COMMENT '已读时间',
  UNIQUE KEY uk_notification_read (username, category, target_id),
  KEY idx_notification_read_user (username, read_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内消息已读回执（含内容快照）';
