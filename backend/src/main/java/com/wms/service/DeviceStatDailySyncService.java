package com.wms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.config.ExternalStatProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从外部系统（WCS / WMS ...）拉取设备每日运行数据，落到本地表 wms_device_stat_daily。
 * 幂等：唯一键 (stat_date, source_sys, device_type, device_code)，重复拉取覆盖更新，可随时手动补跑任意日期。
 */
@Service
@ConditionalOnProperty(prefix = "ext-stat", name = "enabled", havingValue = "true")
public class DeviceStatDailySyncService {
    private static final String INTERFACE_CODE = "DEVICE_STAT_DAILY_SYNC";
    private static final String INTERFACE_NAME = "设备每日运行数据同步";
    /** 响应报文里最多保留多少行明细（正常一天几十行，超量说明源库口径有问题） */
    private static final int DETAIL_LIMIT = 300;
    private static final String UPSERT_SQL =
            "insert into wms_device_stat_daily "
                    + "(stat_date, source_sys, device_type, device_code, running_hours, task_count, total_qty, collected_at, updated_at) "
                    + "values (?,?,?,?,?,?,?,now(),now()) "
                    + "on duplicate key update running_hours = values(running_hours), "
                    + "task_count = values(task_count), total_qty = values(total_qty), updated_at = now()";

    private final Map<String, JdbcTemplate> externalTemplates;
    private final ExternalStatProperties props;
    private final JdbcTemplate localJdbcTemplate;
    private final InterfaceLogService interfaceLogService;
    private final ObjectMapper objectMapper;

    public DeviceStatDailySyncService(@Qualifier("externalStatJdbcTemplates") Map<String, JdbcTemplate> externalTemplates,
                                      ExternalStatProperties props,
                                      JdbcTemplate jdbcTemplate,
                                      InterfaceLogService interfaceLogService,
                                      ObjectMapper objectMapper) {
        this.externalTemplates = externalTemplates;
        this.props = props;
        this.localJdbcTemplate = jdbcTemplate;
        this.interfaceLogService = interfaceLogService;
        this.objectMapper = objectMapper;
    }

    /** 定时任务默认入口：拉取"昨天"的全天数据（当天跑会漏掉晚上才完成的任务） */
    public SyncResult syncYesterday() {
        return sync(LocalDate.now(ZoneId.of(props.getZone())).minusDays(1));
    }

    /**
     * 拉取指定日期，可重复执行。
     *
     * <p>{@code statDate} 为 null 时按"昨天"处理：手动补拉接口的 date 是可选参数，
     * 之前直接把 null 透传到 JDBC 参数构造，导致不传 date 时抛 NPE。
     */
    public SyncResult sync(LocalDate statDate) {
        LocalDate target = statDate != null
                ? statDate
                : LocalDate.now(ZoneId.of(props.getZone())).minusDays(1);
        List<String> details = new ArrayList<>();
        int totalRows = 0;
        int ok = 0;
        int failed = 0;
        for (ExternalStatProperties.Source source : props.getSources()) {
            if (!source.isEnabled()) continue;
            JdbcTemplate template = externalTemplates.get(source.getCode());
            if (template == null) {
                details.add(source.getCode() + "：未配置或数据源未启用，已跳过");
                continue;
            }
            long started = System.currentTimeMillis();
            try {
                Fetched fetched = fetch(source, template, target);
                List<Row> rows = fetched.rows();
                upsert(source, target, rows);
                totalRows += rows.size();
                ok++;
                details.add(source.getCode() + "：" + rows.size() + " 条"
                        + (fetched.dropped() > 0 ? "（丢弃 " + fetched.dropped() + " 条设备号为空的数据）" : ""));
                log(source.getPeer(), source.getQuery() + " | " + target, summary(rows), true, null, started);
            } catch (RuntimeException e) {
                failed++;
                String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                details.add(source.getCode() + "：失败 - " + msg);
                log(source.getPeer(), source.getQuery() + " | " + target, null, false, msg, started);
            }
        }
        return new SyncResult(target, ok, failed, totalRows, details);
    }

    /**
     * 按日期区间逐天补拉，用于首次接入时把最近几天的数据一次性灌进来。
     *
     * <p>每天单独走一次 {@link #sync(LocalDate)}：某天失败不影响其他天，
     * 结果按天返回便于运维看清哪天缺数。入库是 upsert，重复执行不会产生脏数据。</p>
     *
     * @param days 便捷用法：from/to 都不传时，拉「到昨天为止」的最近 N 天
     */
    public List<SyncResult> syncRange(LocalDate from, LocalDate to, Integer days) {
        LocalDate today = LocalDate.now(ZoneId.of(props.getZone()));
        LocalDate start;
        LocalDate end;
        if (from != null || to != null) {
            if (from == null || to == null) throw new IllegalArgumentException("from 与 to 必须同时传入");
            start = from;
            end = to;
        } else {
            int n = days == null || days <= 0 ? 1 : days;
            end = today.minusDays(1);
            start = end.minusDays(n - 1);
        }
        if (end.isBefore(start)) throw new IllegalArgumentException("结束日期不能早于开始日期");
        // 兜个上限：手滑填成 3 年前会把源库扫爆，也不可能有那么多历史要补
        if (start.until(end).getDays() > 366) throw new IllegalArgumentException("一次最多补拉 366 天");
        List<SyncResult> results = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            results.add(sync(d));
        }
        return results;
    }

    private Fetched fetch(ExternalStatProperties.Source source, JdbcTemplate template, LocalDate statDate) {
        Object[] args = dateArgs(source, statDate);
        List<Row> all = template.query(source.getQuery(), (rs, rowNum) -> {
            BigDecimal hours = rs.getBigDecimal("running_hours");
            long tasks = rs.getLong("task_count");
            long qty = 0L;
            // 件数是后加的口径：源库还没加这列时按 0 处理，不让整个源同步失败
            if (hasColumn(rs, "total_qty")) {
                qty = rs.getLong("total_qty");
                if (rs.wasNull()) qty = 0L;
            }
            return new Row(
                    trim(rs.getString("device_type")),
                    trim(rs.getString("device_code")),
                    hours == null ? BigDecimal.ZERO : hours,
                    tasks,
                    qty);
        }, args);
        // 设备号为空的行不能入库：它会污染"按设备累加件数"的口径（空串会被当成同一台设备），
        // 但也不能静默吞掉，丢弃条数会写回同步明细供运维核对。
        List<Row> kept = all.stream().filter(r -> StringUtils.hasText(r.deviceCode())).toList();
        return new Fetched(kept, all.size() - kept.size());
    }

    /**
     * 组装两个日期参数：[统计日, 次日)。
     *
     * <p>源库 stat_date 是 DATE 类型时直接传 {@link Date}；是 varchar 'yyyyMMdd' 这类字符串列时
     * 必须按 {@code date-param-format} 格式化成字符串，否则 MySQL 不会把列隐式转成日期，
     * 条件恒不成立，表现为「同步成功但 0 条」，很难排查。</p>
     */
    private Object[] dateArgs(ExternalStatProperties.Source source, LocalDate statDate) {
        String format = source.getDateParamFormat();
        if (!StringUtils.hasText(format)) {
            return new Object[]{Date.valueOf(statDate), Date.valueOf(statDate.plusDays(1))};
        }
        DateTimeFormatter formatter;
        try {
            formatter = DateTimeFormatter.ofPattern(format.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("source " + source.getCode() + " 的 date-param-format 不是合法日期格式：" + format, e);
        }
        return new Object[]{statDate.format(formatter), statDate.plusDays(1).format(formatter)};
    }

    private boolean hasColumn(java.sql.ResultSet rs, String name) {
        try {
            rs.findColumn(name);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    private void upsert(ExternalStatProperties.Source source, LocalDate statDate, List<Row> rows) {
        if (rows.isEmpty()) return;
        localJdbcTemplate.batchUpdate(UPSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Row row = rows.get(i);
                ps.setDate(1, Date.valueOf(statDate));
                ps.setString(2, source.getCode());
                ps.setString(3, StringUtils.hasText(row.deviceType()) ? row.deviceType() : "UNKNOWN");
                ps.setString(4, row.deviceCode());
                ps.setBigDecimal(5, row.runningHours());
                ps.setLong(6, row.taskCount());
                ps.setLong(7, row.totalQty());
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }

    /**
     * 响应报文只写条数没法排查问题（同步成功但数字不对时看不出是哪台设备错了），
     * 这里把逐行明细一并写进去：设备类型/编码 + 时长/任务数/件数。
     * 明细超过 {@link #DETAIL_LIMIT} 行时只保留前若干条并打标，避免单条日志过大。
     */
    private String summary(List<Row> rows) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("count", rows.size());
        List<Map<String, Object>> details = new ArrayList<>();
        for (int i = 0; i < rows.size() && i < DETAIL_LIMIT; i++) {
            Row row = rows.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("deviceType", row.deviceType());
            item.put("deviceCode", row.deviceCode());
            item.put("runningHours", row.runningHours());
            item.put("taskCount", row.taskCount());
            item.put("totalQty", row.totalQty());
            details.add(item);
        }
        body.put("rows", details);
        if (rows.size() > DETAIL_LIMIT) {
            body.put("rowsTruncated", true);
            body.put("rowsNote", "明细仅保留前 " + DETAIL_LIMIT + " 条，全部数据已入库");
        }
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            return "count=" + rows.size();
        }
    }

    private void log(String peer, String request, String response, boolean success, String error, long started) {
        interfaceLogService.recordOutbound(INTERFACE_CODE, INTERFACE_NAME, "JDBC", peer, request, response,
                success, error, null, System.currentTimeMillis() - started);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    public record Row(String deviceType, String deviceCode, BigDecimal runningHours, Long taskCount, Long totalQty) {
    }

    /** rows = 有效行；dropped = 因设备号为空被丢弃的行数 */
    public record Fetched(List<Row> rows, int dropped) {
    }

    public record SyncResult(LocalDate statDate, int successSources, int failedSources, int rows, List<String> details) {
    }
}
