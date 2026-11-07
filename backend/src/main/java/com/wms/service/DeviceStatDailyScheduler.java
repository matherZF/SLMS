package com.wms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 每晚从 WCS/WMS 外部库拉取前一天的设备运行数据。
 *
 * <p>多实例部署时靠 {@link SchedulerLockService} 保证同一时刻只有一台机器执行，
 * 避免外部库被重复扫两遍（虽然入库是 upsert，但重复拉取会污染 InterfaceLog 且拖慢外部库）。
 */
@Component
@ConditionalOnProperty(prefix = "ext-stat", name = "enabled", havingValue = "true")
public class DeviceStatDailyScheduler {
    private static final Logger log = LoggerFactory.getLogger(DeviceStatDailyScheduler.class);
    private static final String LOCK_TASK = "device-stat-daily-pull";
    private static final Duration LEASE = Duration.ofHours(2);

    private final DeviceStatDailySyncService service;
    private final SchedulerLockService lock;

    public DeviceStatDailyScheduler(DeviceStatDailySyncService service, SchedulerLockService lock) {
        this.service = service;
        this.lock = lock;
    }

    @Scheduled(cron = "${ext-stat.cron:0 30 1 * * *}", zone = "${ext-stat.zone:Asia/Shanghai}")
    public void nightlyPull() {
        // 返回 null 表示没抢到锁（另一实例在执行），此时不打日志噪音
        DeviceStatDailySyncService.SyncResult result = lock.callIfLocked(LOCK_TASK, LEASE, service::syncYesterday, null);
        if (result == null) return;
        log.info("[设备运行数据同步] 日期={} 成功源={} 失败源={} 条数={} 明细={}",
                result.statDate(), result.successSources(), result.failedSources(), result.rows(), result.details());
    }
}
