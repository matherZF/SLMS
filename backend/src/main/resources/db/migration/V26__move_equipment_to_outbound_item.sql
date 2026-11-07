-- 领用设备从单头下放到明细
--
-- 一张领用单可以有多条明细，各明细的备件往往装在不同设备上。
-- 设备原先只存在单头，多明细跨设备时所有明细都被记到同一台设备上，
-- 台账建档跟着全部挂错——寿命、更换周期、需求预测全部失真。
--
-- 设备改挂在明细上，一条明细一台设备。历史单据每单只有一条明细，
-- 把单头的设备回填到明细即可，不会改变台账结果。

ALTER TABLE wms_outbound_item
    ADD COLUMN equipment_id            bigint NULL AFTER product_id,
    ADD COLUMN equipment_code_snapshot varchar(64) NULL AFTER equipment_id,
    ADD COLUMN equipment_name_snapshot varchar(128) NULL AFTER equipment_code_snapshot;

ALTER TABLE wms_outbound_item
    ADD CONSTRAINT fk_outbound_item_equipment FOREIGN KEY (equipment_id) REFERENCES wms_equipment (id);

UPDATE wms_outbound_item i
    JOIN wms_outbound_order o ON o.id = i.outbound_order_id
SET i.equipment_id            = o.equipment_id,
    i.equipment_code_snapshot = o.equipment_code_snapshot,
    i.equipment_name_snapshot = o.equipment_name_snapshot
WHERE o.equipment_id IS NOT NULL
  AND i.equipment_id IS NULL;
