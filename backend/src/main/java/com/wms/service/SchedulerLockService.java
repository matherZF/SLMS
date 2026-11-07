package com.wms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * 定时任务的集群互斥锁（等价 ShedLock 的 lockAtMostFor 语义）。
 *
 * <p>SP 计划部署在同网段的虚拟化服务器上，只要出现两实例并存（滚动重启、主备切换、误扩容），
 * 裸 {@code @Scheduled} 就会把同一天的数据处理两遍：设备运行数据是 upsert 尚可，
 * 备件使用台账的 {@code refreshAll} 会重复写快照、重复推进寿命计数。
 *
 * <p>实现用 {@code wms_scheduler_lock} 一张租约表，不引入外部依赖：
 * <ul>
 *   <li>抢锁：{@code INSERT}，主键冲突说明锁已被别的实例持有</li>
 *   <li>冲突时：{@code UPDATE ... WHERE locked_until < 当前时间}，只有租约过期才能接手（对方进程挂了或跑超时）</li>
 *   <li>释放：{@code DELETE} 自己的锁行，下一次调度直接 INSERT 成功，正常路径不产生额外日志</li>
 *   <li>进程崩溃时锁行留着，租约自然过期后可被接手，不会死锁</li>
 * </ul>
 *
 * <p>租约 {@code lease} 必须大于任务最长执行时间，否则任务还没跑完锁就被别人接手了。
 * 宁可给大一点：任务正常结束会主动释放，租约只兜底"进程直接没了"的情况。
 */
@Component
public class SchedulerLockService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerLockService.class);

    private final JdbcTemplate jdbcTemplate;
    /** 当前实例标识，只用于排查"这个锁是谁占着的" */
    private final String owner;

    public SchedulerLockService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.owner = resolveOwner();
    }

    /**
     * 尝试抢锁，成功返回 true。
     *
     * <p>抢不到不是异常：另一台机器正在跑同一个任务，本实例直接跳过本次调度。
     */
    public boolean tryLock(String task, Duration lease) {
        Instant now = Instant.now();
        Timestamp nowTs = Timestamp.from(now);
        Timestamp untilTs = Timestamp.from(now.plus(lease));
        try {
            // 正常路径：上一次执行已经把锁行删掉了，这里直接插入成功
            jdbcTemplate.update(
                    "insert into wms_scheduler_lock (task_name, locked_until, locked_by, updated_at) values (?, ?, ?, ?)",
                    task, untilTs, owner, nowTs);
            return true;
        } catch (DuplicateKeyException stillLocked) {
            // 锁行还在：要么别的实例正在跑，要么上次进程崩了留下过期租约
        }
        // 只有租约已过期（或历史数据为 null）才能接手；用 nowTs 做比较基准，避免依赖数据库时钟
        int claimed = jdbcTemplate.update(
                "update wms_scheduler_lock set locked_until = ?, locked_by = ?, updated_at = ? "
                        + "where task_name = ? and (locked_until is null or locked_until < ?)",
                untilTs, owner, nowTs, task, nowTs);
        if (claimed > 0) {
            // 能抢到过期租约说明上一个持有者没正常释放（进程被杀或跑超时），这种要留痕
            log.warn("[定时锁] 任务 {} 的上一个租约已过期，由 {} 接手（上次执行可能异常中断）", task, owner);
            return true;
        }
        return false;
    }

    /** 释放锁：删掉自己的锁行。只有持有者删得掉，避免 A 跑完把 B 刚抢到的锁解掉。 */
    public void unlock(String task) {
        try {
            jdbcTemplate.update("delete from wms_scheduler_lock where task_name = ? and locked_by = ?", task, owner);
        } catch (RuntimeException e) {
            // 释放失败不影响业务：租约到期后会被下一台机器接手
            log.warn("[定时锁] 释放任务 {} 失败：{}", task, e.getMessage());
        }
    }

    /**
     * 抢到锁才执行，执行完自动释放。
     *
     * @return true 表示本次执行了任务；false 表示被其他实例持有而跳过
     */
    public boolean runIfLocked(String task, Duration lease, Runnable action) {
        if (!tryLock(task, lease)) {
            log.info("[定时锁] 任务 {} 正由其他实例执行，本次跳过", task);
            return false;
        }
        try {
            action.run();
            return true;
        } finally {
            unlock(task);
        }
    }

    /** 带返回值的版本，方便需要记录执行结果的场景 */
    public <T> T callIfLocked(String task, Duration lease, Supplier<T> action, T skipped) {
        if (!tryLock(task, lease)) {
            log.info("[定时锁] 任务 {} 正由其他实例执行，本次跳过", task);
            return skipped;
        }
        try {
            return action.get();
        } finally {
            unlock(task);
        }
    }

    private static String resolveOwner() {
        try {
            String jvm = ManagementFactory.getRuntimeMXBean().getName(); // 形如 12345@hostname
            return jvm.length() > 100 ? jvm.substring(0, 100) : jvm;
        } catch (RuntimeException e) {
            return "unknown";
        }
    }
}
