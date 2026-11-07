-- 使用设备改为由「基础数据 → 设备档案 → 关联设备备件」自动决定，出库单不再手选设备。
--
-- 旧逻辑只在出库单指定了设备时才把设备写进台账，没选就一直为空，台账永远停在
-- BASELINE_PENDING（页面显示「待补基准」）。这里按关联表回填历史记录：
-- 只回填「该备件唯一关联一台设备」的，多台关联仍保持未绑定（无法判断基准设备）。
--
-- 注意：本脚本只补设备号，不建基准。基准值要从设备累计量取，应用侧刷新时才计算，
-- 回填后点一次台账的「刷新」（或等夜间同步）即可把 baseline_status 从 PENDING 置为 READY。

UPDATE wms_spare_part_usage u
JOIN (
    SELECT ep.product_id,
           MIN(ep.equipment_id) AS equipment_id,
           COUNT(DISTINCT ep.equipment_id) AS device_count
    FROM wms_equipment_part ep
    GROUP BY ep.product_id
) t ON t.product_id = u.product_id AND t.device_count = 1
JOIN wms_equipment e ON e.id = t.equipment_id
SET u.equipment_id = e.id,
    u.equipment_code = e.code,
    u.equipment_name = e.name,
    -- 旧的「没绑设备」提示已经失效，清掉；其它真实数据异常保留
    u.data_error = CASE
        WHEN u.data_error LIKE '%未关联设备%' OR u.data_error LIKE '%未绑定设备%'
            OR u.data_error LIKE '%未在基础数据中关联设备%' THEN NULL
        ELSE u.data_error END
WHERE u.equipment_id IS NULL;
