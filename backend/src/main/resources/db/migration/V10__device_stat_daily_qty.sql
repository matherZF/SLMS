-- V10: 设备每日运行数据汇总表增加"件数"
-- 只有任务数不够看设备强度：有的任务搬 1 件，有的搬 30 件，
-- 件数（吞吐）才是输送线滚轮/皮带/链条这类"按产量磨损"备件的真实计量口径。
ALTER TABLE wms_device_stat_daily
    ADD COLUMN total_qty BIGINT NOT NULL DEFAULT 0 COMMENT '当日处理件数（件烟/料箱累计数量）' AFTER task_count;
