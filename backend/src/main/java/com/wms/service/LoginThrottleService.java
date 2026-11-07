package com.wms.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限流：同一用户名在 15 分钟窗口内失败 5 次即锁定 15 分钟。
 *
 * 说明：采用进程内计数，适合当前单实例部署形态；若将来多实例负载均衡，
 * 应替换为 Redis 计数（键结构与本类保持一致即可平滑迁移）。
 * 这里不引入额外依赖，避免为一个限流功能影响内网离线部署。
 */
@Service
public class LoginThrottleService {

    private static final int MAX_FAILS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_ENTRIES = 10_000;

    private record Attempt(int fails, Instant windowStart, Instant lockedUntil) {}

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /** 返回剩余锁定秒数，0 表示未锁定 */
    public long lockedSeconds(String username) {
        String k = key(username);
        Attempt a = attempts.get(k);
        if (a == null || a.lockedUntil() == null) return 0;
        long seconds = Duration.between(Instant.now(), a.lockedUntil()).getSeconds();
        if (seconds <= 0) {
            attempts.remove(k);
            return 0;
        }
        return seconds;
    }

    public void recordFailure(String username) {
        // 防御性上限：避免账号名被枚举时把内存撑爆
        if (attempts.size() > MAX_ENTRIES) attempts.clear();
        String k = key(username);
        Instant now = Instant.now();
        attempts.compute(k, (ignored, old) -> {
            if (old == null || Duration.between(old.windowStart(), now).compareTo(WINDOW) > 0) {
                return new Attempt(1, now, null);
            }
            int fails = old.fails() + 1;
            Instant lock = fails >= MAX_FAILS ? now.plus(WINDOW) : null;
            return new Attempt(fails, old.windowStart(), lock);
        });
    }

    public void clear(String username) {
        attempts.remove(key(username));
    }

    private String key(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
