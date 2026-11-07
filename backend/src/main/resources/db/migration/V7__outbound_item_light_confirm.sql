-- 出库项：亮灯命令 token + 亮灯结果确认
-- cmdtoken 由 startPicking 生成 UUID，塞进 lightTagsLed 请求体，墨水屏服务器原样回传到回调
ALTER TABLE wms_outbound_item
  ADD COLUMN light_cmdtoken      VARCHAR(64)  NULL COMMENT '亮灯命令 token（UUID），用于请求-回调匹配',
  ADD COLUMN light_confirmed      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '亮灯结果回调是否已收到并匹配（1=成功，2=失败）',
  ADD COLUMN light_confirmed_at   DATETIME     NULL COMMENT '亮灯结果回调时间',
  ADD COLUMN light_message        VARCHAR(255)  NULL COMMENT '亮灯结果回调 message 原文';

CREATE INDEX idx_outbound_item_cmdtoken ON wms_outbound_item (light_cmdtoken);
