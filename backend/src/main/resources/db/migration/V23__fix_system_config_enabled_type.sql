-- V22 里 enabled 建成了 tinyint，与 Hibernate 把 boolean 映射成 bit(1) 的约定不一致，
-- 启动时 schema-validation 会报 "wrong column type ... expecting bit"。这里改回 bit。
-- 项目里其余表的布尔列（wms_product.enabled、wms_spare_part_alert.read_flag 等）都是这个类型。
alter table wms_system_config modify column enabled bit not null default b'1';
