-- ============================================================
-- V35: 启动初始化标记
--
-- 背景：DataInitializer 以前每次启动都会"补齐"角色、字典、审批流程、系统配置
--       （缺哪条补哪条）。结果是运维在界面上清理掉的字典项 / 示例数据，
--       服务一重启又全部冒出来，看起来像"删不掉"。
--
-- 方案：加一张单行标记表。只有"从没初始化过"的库才会执行一次性数据注入，
--       写完打标记，之后每次启动直接跳过。
--
-- 存量库兼容（重要）：本脚本在 DataInitializer 执行之前跑。如果库里已经有用户，
--       说明这台库早就初始化过了 —— 此时直接把标记写上，这样升级到本版本后
--       不会再触发一次注入，人工清理过的数据不会被写回来。
--       全新库在迁移阶段 wms_user 还是空的，不会打标记，
--       由 DataInitializer 完成首次注入后再自己打标记。
-- ============================================================
CREATE TABLE IF NOT EXISTS wms_bootstrap_marker (
  marker     VARCHAR(64) NOT NULL PRIMARY KEY COMMENT '一次性初始化阶段的标记',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '打标时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='启动初始化标记（防止重启重复注入初始数据）';

INSERT IGNORE INTO wms_bootstrap_marker (marker)
SELECT 'DATA_SEED_V1' FROM wms_user LIMIT 1;
