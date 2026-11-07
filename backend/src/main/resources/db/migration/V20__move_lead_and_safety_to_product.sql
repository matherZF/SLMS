-- ============================================================
-- 采购周期、安全库存从「设备×备件」上收到「备件档案」
--
-- 这两项都是备件的固有属性，不随装在哪台设备变化：
--   · 采购周期 = 供应商到货天数，同一个 SKU 只有一个值；
--   · 安全库存 = 仓库对这个 SKU 的保底量，库存本来就是多台设备共享的。
-- 放在关联表上会导致同一备件在不同设备填出不同值（现场 SP-001 设备级 120、
-- 备件级 0），补货点一时按 130 报缺货、一时按 0 说不缺，没人能判断哪个对。
--
-- 设备侧只保留真正属于这台设备的：单机数量、更换周期。
-- 补货点改为：备件档案安全库存 + ⌈备件档案采购周期 ÷ 本设备更换周期⌉ × 单机数量。
--
-- 迁移前先按「取最大」把已录入的设备级数值提升到备件档案，避免丢数据。
-- ============================================================

UPDATE wms_product
   SET safety_stock = GREATEST(safety_stock,
           COALESCE((SELECT MAX(ep.safety_stock) FROM wms_equipment_part ep
                      WHERE ep.product_id = wms_product.id), 0)),
       procurement_lead_days = GREATEST(procurement_lead_days,
           COALESCE((SELECT MAX(ep.procurement_lead_days) FROM wms_equipment_part ep
                      WHERE ep.product_id = wms_product.id), 0));

ALTER TABLE wms_equipment_part
    DROP COLUMN procurement_lead_days,
    DROP COLUMN safety_stock;
