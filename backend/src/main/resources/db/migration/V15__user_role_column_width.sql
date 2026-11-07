-- ---------------------------------------------------------------------------
-- 用户表角色列宽与角色表对齐
--
-- 背景：wms_role.code 是 varchar(32)，RoleService.normalize 也允许 2~32 位编码，
--       但 wms_user.role 从 V1 起是 varchar(16)。结果：管理员能建出
--       MAINTENANCE_SUPERVISOR 这类 20 字符的角色，却永远分配不到用户
--       （MySQL STRICT 模式下直接 1406 Data too long）。
--
-- 这里把用户表跟着扩到 32，与 wms_role.code 保持一致。
-- 重复执行是安全的（已扩过再 ALTER 成同样的 32 不会报错）。
-- ---------------------------------------------------------------------------
ALTER TABLE `wms_user`
    MODIFY COLUMN `role` VARCHAR(32) NOT NULL COMMENT '角色编码，对应 wms_role.code';
