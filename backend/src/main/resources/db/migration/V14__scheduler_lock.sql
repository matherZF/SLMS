-- V14: 定时任务集群锁
--
-- 背景（详见 docs/SYSTEM-REVIEW-2026-10-29.md）：
--   SparePartUsageScheduler / DeviceStatDailyScheduler 都是裸 @Scheduled，
--   单机运行时没问题，一旦按虚拟化方案部署两个实例（哪怕只是灰度重启时的短暂双活），
--   同一天的数据会被拉取两遍：设备运行数据是幂等 upsert 还好，
--   备件使用台账 refreshAll 会重复写快照、重复推进寿命计数。
--
-- 方案说明：原计划用 ShedLock，但本机/内网的 Maven 镜像对 net.javacr 整组返回 404
--   （net.bytebuddy 等常规依赖正常），无法引入新依赖。
--   这里用一张租约表实现等价的"同一任务同一时刻只有一台机器执行"：
--     - 抢锁 = INSERT，主键 task_name 冲突即说明已有实例在跑
--     - 冲突时 = UPDATE ... WHERE locked_until < NOW()，只有租约过期才能接手（对方崩溃或跑超时）
--     - 执行完 = DELETE 自己的锁行，下一次调度直接 INSERT 成功，正常路径不留痕
--     - 进程崩溃时锁行留着，租约到期后可被接手，不会永久死锁
--   语义与 ShedLock 的 lockAtMostFor 一致，代价是零依赖。
--
-- 该表只存锁状态，可随时清空，清空前请确认没有任务正在执行。

CREATE TABLE IF NOT EXISTS `wms_scheduler_lock` (
  `task_name`    VARCHAR(64)  NOT NULL COMMENT '任务名，与 SchedulerLockService 传入的常量一致',
  `locked_until` DATETIME(6)  NULL     COMMENT '租约到期时间；早于当前时间表示可被抢占',
  `locked_by`    VARCHAR(128) NULL     COMMENT '持有者（主机名 + 进程号），仅用于排查',
  `updated_at`   DATETIME(6)  NOT NULL COMMENT '最后一次抢锁/释放时间',
  PRIMARY KEY (`task_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='定时任务集群锁（自建，等价 ShedLock）';
