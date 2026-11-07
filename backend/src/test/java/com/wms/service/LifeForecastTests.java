package com.wms.service;

import com.wms.dto.LifeMetricRule;
import com.wms.dto.LifeMetricUsageVO;
import com.wms.entity.SparePartUsage;
import com.wms.entity.SparePartUsageSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 备件更换预测：由快照拟合日均消耗，再外推寿命耗尽日。
 *
 * <p>关键取舍都写在这里当回归点：跨度不足不预测、设备闲置不预测、
 * 累计值回退（设备换计数器）不预测，避免页面出现"下周就要换"这类假紧急。</p>
 */
class LifeForecastTests {

    private static final Instant NOW = Instant.parse("2026-10-30T02:00:00Z");

    private static SparePartUsage usage(String metric, String rated, int ratedDaysAgo) {
        SparePartUsage u = new SparePartUsage();
        u.setLifeMetric(metric);
        u.setRatedLifeValue(new BigDecimal(rated));
        u.setWarningPercent(80);
        u.setStartedAt(NOW.minus(Duration.ofDays(ratedDaysAgo)));
        return u;
    }

    private static List<LifeMetricRule> rule(String metric, String rated) {
        return List.of(new LifeMetricRule(metric, new BigDecimal(rated)));
    }

    private static SparePartUsageSnapshot snapshot(Instant at, Long pieceQty) {
        SparePartUsageSnapshot s = new SparePartUsageSnapshot();
        s.setCollectedAt(at);
        s.setPieceQty(pieceQty);
        return s;
    }

    @Test
    void 按首尾累计值之差算日均消耗() {
        // 3 天里累计从 1000 涨到 4000 → 3000/3 = 1000 件/天
        List<SparePartUsageSnapshot> snaps = List.of(
                snapshot(NOW.minus(Duration.ofDays(3)), 1000L),
                snapshot(NOW.minus(Duration.ofDays(2)), 2000L),
                snapshot(NOW, 4000L));

        LifeMetrics.Rate rate = LifeMetrics.rates(snaps, rule("QTY", "50000"), 3).get("QTY");

        assertNotNull(rate);
        assertEquals(0, new BigDecimal("1000").compareTo(rate.perDay()));
        assertEquals(3, rate.sampleDays());
    }

    @Test
    void 样本跨度不足时不输出速率() {
        List<SparePartUsageSnapshot> snaps = List.of(
                snapshot(NOW.minus(Duration.ofDays(1)), 1000L),
                snapshot(NOW, 2000L));

        LifeMetrics.Rate rate = LifeMetrics.rates(snaps, rule("QTY", "50000"), 3).get("QTY");

        assertNotNull(rate);
        assertNull(rate.perDay(), "跨度 1 天不足 3 天，不预测");
        assertEquals(1, rate.sampleDays(), "仍要回报已积累的天数供页面提示");
    }

    @Test
    void 只有一个采样点时不产生速率() {
        List<SparePartUsageSnapshot> snaps = List.of(snapshot(NOW, 4000L));

        assertNull(LifeMetrics.rates(snaps, rule("QTY", "50000"), 1).get("QTY"));
    }

    @Test
    void 剩余量除以日耗得到寿命耗尽日() {
        SparePartUsage u = usage("QTY", "50000", 10);
        u.setBaselinePieceQty(1000L);
        u.setCurrentPieceQty(40000L);
        Map<String, LifeMetrics.Rate> rates = Map.of("QTY", new LifeMetrics.Rate(new BigDecimal("1000"), 5));

        LifeMetricUsageVO row = LifeMetrics.evaluate(u, rule("QTY", "50000"), rates, NOW).get(0);

        assertEquals(0, new BigDecimal("11000").compareTo(row.remainingLifeValue()));
        assertNotNull(row.expectedEndAt());
        assertEquals(11, Duration.between(NOW, row.expectedEndAt()).toDays());
    }

    @Test
    void 天数口径不需要速率直接算到期日() {
        SparePartUsage u = usage("DAYS", "30", 10);

        LifeMetricUsageVO row = LifeMetrics.evaluate(u, rule("DAYS", "30"), Map.of(), NOW).get(0);

        assertEquals(NOW.minus(Duration.ofDays(10)).plus(Duration.ofDays(30)), row.expectedEndAt());
        assertNull(row.dailyRate(), "纯天数口径没有日均消耗的概念");
    }

    @Test
    void 设备闲置时不给预测日期() {
        SparePartUsage u = usage("QTY", "50000", 10);
        u.setBaselinePieceQty(1000L);
        u.setCurrentPieceQty(40000L);
        Map<String, LifeMetrics.Rate> rates = Map.of("QTY", new LifeMetrics.Rate(BigDecimal.ZERO, 5));

        LifeMetricUsageVO row = LifeMetrics.evaluate(u, rule("QTY", "50000"), rates, NOW).get(0);

        assertNull(row.expectedEndAt(), "速率为 0 外推不出来，不能给出无限远的日期");
    }

    @Test
    void 寿命已耗尽时立即到期() {
        SparePartUsage u = usage("QTY", "50000", 10);
        u.setBaselinePieceQty(1000L);
        u.setCurrentPieceQty(60000L);
        Map<String, LifeMetrics.Rate> rates = Map.of("QTY", new LifeMetrics.Rate(new BigDecimal("1000"), 5));

        LifeMetricUsageVO row = LifeMetrics.evaluate(u, rule("QTY", "50000"), rates, NOW).get(0);

        assertEquals(NOW, row.expectedEndAt());
    }

    @Test
    void 建议更换日取两者较早() {
        Instant near = NOW.plus(Duration.ofDays(5));
        Instant far = NOW.plus(Duration.ofDays(20));

        assertEquals(near, LifeMetrics.earliest(near, far));
        assertEquals(near, LifeMetrics.earliest(far, near));
        assertEquals(near, LifeMetrics.earliest(null, near), "只有计划日时按计划日");
        assertNull(LifeMetrics.earliest(null, null));
    }

    @Test
    void 更换日按最早耗尽的取而不是百分比最高的() {
        // 25 天前装上，天数口径还剩 5 天；件数口径才用 1%，还要 990 天
        SparePartUsage u = usage("DAYS", "30", 25);
        u.setBaselinePieceQty(0L);
        u.setCurrentPieceQty(1000L);
        List<LifeMetricRule> rules = List.of(
                new LifeMetricRule("DAYS", new BigDecimal("30")),
                new LifeMetricRule("QTY", new BigDecimal("100000")));
        Map<String, LifeMetrics.Rate> rates = Map.of("QTY", new LifeMetrics.Rate(new BigDecimal("100"), 5));

        List<LifeMetricUsageVO> rows = LifeMetrics.evaluate(u, rules, rates, NOW);

        assertEquals("DAYS", LifeMetrics.dominant(rows).metric(), "按百分比取严是天数口径");
        assertEquals(NOW.minus(Duration.ofDays(25)).plus(Duration.ofDays(30)),
                LifeMetrics.soonest(rows).expectedEndAt(),
                "更换日必须按最先耗尽的算，否则会晚几个月");
    }

    @Test
    void 所有口径都算不出日期时没有更换日() {
        SparePartUsage u = usage("QTY", "50000", 10);

        List<LifeMetricUsageVO> rows = LifeMetrics.evaluate(u, rule("QTY", "50000"), Map.of(), NOW);

        assertNull(LifeMetrics.soonest(rows), "无基准也无速率，不能编一个日期出来");
    }
}
