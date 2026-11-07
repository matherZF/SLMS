package com.wms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 每天早上算一次备件预警并落表。
 *
 * <p>时间点排在设备数据同步（01:30）与台账刷新（02:00）之后，算的时候用的是当天最新的消耗进度。
 * 若源库跑批晚于 01:30，这里算到的会是前一天的数据——那是数据源的时间问题，不是本任务的问题。</p>
 *
 * <p>预警只在打开页面时实时算的话，没人打开就等于没预警；落表之后才有站内消息、去重和历史。</p>
 */
@Component
@ConditionalOnProperty(prefix = "spare-part.alert", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AlertSnapshotScheduler {
    private static final Logger log = LoggerFactory.getLogger(AlertSnapshotScheduler.class);
    private static final String LOCK_TASK = "spare-part-alert-snapshot";
    private static final Duration LEASE = Duration.ofHours(2);

    private final AlertService service;
    private final SchedulerLockService lock;

    public AlertSnapshotScheduler(AlertService service, SchedulerLockService lock) {
        this.service = service;
        this.lock = lock;
    }

    @Scheduled(cron = "${spare-part.alert.cron:0 0 9 * * *}", zone = "${spare-part.alert.zone:Asia/Shanghai}")
    public void snapshot() {
        lock.runIfLocked(LOCK_TASK, LEASE, () -> {
            int created = service.snapshot();
            log.info("[预警] 定时计算完成，本次新增 {} 条", created);
        });
    }
}
