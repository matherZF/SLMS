package com.wms.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 每晚刷新备件使用台账（寿命推算）。
 *
 * <p>多实例部署时靠 {@link SchedulerLockService} 保证同一时刻只有一台机器执行；
 * 租约给 4 小时，远大于正常耗时，只用于兜底"进程直接崩掉、锁没释放"的情况。
 */
@Component
@ConditionalOnProperty(prefix = "spare-part.usage-sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SparePartUsageScheduler {
    private static final String LOCK_TASK = "spare-part-usage-refresh";
    private static final Duration LEASE = Duration.ofHours(4);

    private final SparePartUsageService service;
    private final SchedulerLockService lock;

    public SparePartUsageScheduler(SparePartUsageService service, SchedulerLockService lock) {
        this.service = service;
        this.lock = lock;
    }

    @Scheduled(cron = "${spare-part.usage-sync.cron:0 0 2 * * *}", zone = "${spare-part.usage-sync.zone:Asia/Shanghai}")
    public void nightlyRefresh() {
        lock.runIfLocked(LOCK_TASK, LEASE, () -> service.refreshAll("SCHEDULED"));
    }
}
