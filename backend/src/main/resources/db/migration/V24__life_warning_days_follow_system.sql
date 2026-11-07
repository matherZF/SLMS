-- 备件上的「寿命预警提前天数」为 0 时表示跟随系统配置（spare-part.life.warning-days），
-- 非 0 才是这个备件单独设的值。
--
-- V22 迁移时给所有备件填了具体值 3，等于每个备件都单独设了一份，
-- 系统配置里改多少都会被它盖掉——那样配置项就形同虚设了。这里统一改回 0。
alter table wms_product alter column life_warning_days set default 0;
alter table wms_spare_part_usage alter column warning_days set default 0;
update wms_product set life_warning_days = 0;
update wms_spare_part_usage set warning_days = 0;
