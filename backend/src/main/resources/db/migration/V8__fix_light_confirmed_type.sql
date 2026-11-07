-- 修正 V7 遗留问题：MySQL 的 TINYINT(1) 被 Hibernate 当成 BIT 类型，
-- 与实体 lightConfirmed (Integer / int null) 的 Schema 校验冲突。
-- 改成普通 INT 即可消除 Hibernate Schema-validation 错误。
ALTER TABLE wms_outbound_item
  MODIFY COLUMN light_confirmed INT NULL COMMENT '亮灯结果回调状态：null=未收到，1=result=true，2=result=false';
