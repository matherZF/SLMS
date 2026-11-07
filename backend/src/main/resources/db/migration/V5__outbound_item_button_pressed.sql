-- V5: 出库拣货项增加电子标签按键确认字段
-- 当电子标签按键事件回调（enterKey）收到后，标记对应出库项的 button_pressed=true，
-- completePicking() 要求所有 item.button_pressed=true 才可完成出库。
ALTER TABLE wms_outbound_item
  ADD COLUMN button_pressed   bit(1)    NOT NULL DEFAULT b'0' COMMENT '电子标签右下按键已确认',
  ADD COLUMN button_pressed_at datetime NULL     COMMENT '按键确认时间';

CREATE INDEX idx_outbound_item_location ON wms_outbound_item(location_id);
