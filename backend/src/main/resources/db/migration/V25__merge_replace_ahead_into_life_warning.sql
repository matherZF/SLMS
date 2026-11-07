-- 「更换预警提前量」与「寿命预警提前天数」合并为一个
--
-- 两者描述的是同一件事：备件距预计更换日还剩多少天开始提醒。
-- 更麻烦的是实际只有 spare-part.alert.replace-ahead-days 被代码读取，
-- spare-part.life.warning-days 从未参与任何计算——改它系统毫无反应。
--
-- 保留语义更清楚、且和备件档案上「寿命预警提前(天)」同名的 spare-part.life.warning-days，
-- 另一个键删除。若运营改过被删那个键的值，先把值顶过来，不丢配置。

UPDATE wms_system_config dst
    JOIN wms_system_config src ON src.config_key = 'spare-part.alert.replace-ahead-days'
SET dst.config_value = src.config_value
WHERE dst.config_key = 'spare-part.life.warning-days'
  AND src.config_value <> src.default_value;

DELETE FROM wms_system_config WHERE config_key = 'spare-part.alert.replace-ahead-days';

UPDATE wms_system_config
SET label = '寿命预警提前天数',
    remark = '预计更换日临近这么多天就提醒更换；备件档案上的「寿命预警提前(天)」填 0 时用这个值。'
WHERE config_key = 'spare-part.life.warning-days';
