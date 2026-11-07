package com.wms.service;

import com.wms.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 业务单号发号器（号段表实现）。
 * <p>
 * 替代原来"查当天最大值 + 1"的写法，那个写法有两个硬伤：
 * <ul>
 *   <li>{@code synchronized} 只包住"读最大值"，读完之后到 insert+commit 之间没有任何保护，
 *       两个线程完全可能读到同一个值，撞唯一键直接建单失败；</li>
 *   <li>序号宽度固定 4 位，单日超过 9999 单后字符串排序失效，会一直取到同一个"最大值"，
 *       从此永久撞号。</li>
 * </ul>
 * 这里用 MySQL 的 {@code INSERT ... ON DUPLICATE KEY UPDATE next_val = LAST_INSERT_ID(next_val + 1)}
 * 在行锁内原子地取号：并发调用的线程会在号段行上串行排队，每个线程拿到的号一定唯一。
 * <p>
 * 关于 {@code LAST_INSERT_ID}：它是<b>会话级</b>变量，所以取号与读回必须在同一个连接上完成。
 * 这里用一次 {@link ConnectionCallback} 把两条语句绑在同一连接上，避免连接池把两条语句分到不同物理连接。
 * <p>
 * 号段键按"前缀 + 日期"划分（如 {@code OUT20261029}），跨天自动重置为 1，
 * 单号形如 {@code OUT20261029-000001}。发号只会单调递增，业务回滚造成的跳号是有意为之——
 * 号段不复用，才能保证历史单号的绝对唯一。
 */
@Service
public class NumberSequenceService {
    private static final Logger log = LoggerFactory.getLogger(NumberSequenceService.class);

    /** 单号序号宽度。6 位可支撑单日 999999 单，且定宽保证字符串排序与数值排序一致 */
    public static final int SEQ_WIDTH = 6;

    private static final String UPSERT_SQL =
            "insert into wms_number_sequence (seq_key, next_val, updated_at) "
                    + "values (?, last_insert_id(1), now(6)) "
                    + "on duplicate key update next_val = last_insert_id(next_val + 1), updated_at = now(6)";

    private static final String READ_SQL = "select last_insert_id()";

    private static final int MAX_ATTEMPTS = 3;

    private final JdbcTemplate jdbc;

    public NumberSequenceService(@Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 取下一个序号。
     *
     * @param prefix 单号前缀，如 {@code OUT}、{@code PA}、{@code B}
     * @return 当天该前缀下的序号，从 1 开始
     */
    public long next(String prefix) {
        if (!StringUtils.hasText(prefix)) throw new BizException("单号前缀不能为空");
        return nextByKey(prefix.trim().toUpperCase() + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
    }

    /** 按完整号段键取号（键长度上限与表字段一致） */
    public long nextByKey(String seqKey) {
        if (!StringUtils.hasText(seqKey) || seqKey.length() > 64) {
            throw new BizException("号段键不合法");
        }
        DataAccessException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                Long allocated = jdbc.execute((ConnectionCallback<Long>) con -> allocate(con, seqKey));
                if (allocated != null) return allocated;
            } catch (DataAccessException e) {
                // 死锁/锁等待超时都属于可重试的瞬时错误，重试即可拿到新号
                lastError = e;
                log.warn("[Seq] 号段 {} 第 {} 次取号失败，准备重试：{}", seqKey, attempt, e.getMessage());
            }
        }
        log.error("[Seq] 号段 {} 连续 {} 次取号失败", seqKey, MAX_ATTEMPTS, lastError);
        throw new BizException("单号分配失败，请稍后重试");
    }

    /** 生成带定宽序号的单号，如 {@code OUT20261029-000001} */
    public String nextOrderNo(String prefix) {
        return prefix + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + String.format("%0" + SEQ_WIDTH + "d", next(prefix));
    }

    /** 取号并读回，两条语句必须共用同一个连接（LAST_INSERT_ID 是会话级的） */
    private Long allocate(Connection con, String seqKey) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(UPSERT_SQL)) {
            ps.setString(1, seqKey);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = con.prepareStatement(READ_SQL);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : null;
        }
    }
}
