package com.wms.service;

import com.wms.dto.LifeMetricRule;
import com.wms.dto.LifeMetricUsageVO;
import com.wms.entity.SparePartUsage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 多口径寿命计量的核心逻辑验证（纯计算，不依赖 Spring 上下文）。
 */
class LifeMetricTests {

    private SparePartUsage usage(long baselineHours, long currentHours,
                                 long baselineTasks, long currentTasks,
                                 long baselinePieces, long currentPieces) {
        SparePartUsage u = new SparePartUsage();
        u.setBaselineRunningHours(BigDecimal.valueOf(baselineHours));
        u.setCurrentRunningHours(BigDecimal.valueOf(currentHours));
        u.setBaselineTaskCount(baselineTasks);
        u.setCurrentTaskCount(currentTasks);
        u.setBaselinePieceQty(baselinePieces);
        u.setCurrentPieceQty(currentPieces);
        u.setStartedAt(Instant.now().minus(100, ChronoUnit.DAYS));
        return u;
    }

    @Test
    void legacySingleMetricStillParsesAsOneRule() {
        List<LifeMetricRule> rules = LifeMetrics.parse(null, "hours", new BigDecimal("8000"));
        assertEquals(1, rules.size());
        assertEquals("HOURS", rules.get(0).metric());
        assertEquals(0, rules.get(0).rated().compareTo(new BigDecimal("8000")));
    }

    @Test
    void noneMetricYieldsNoRule() {
        assertTrue(LifeMetrics.parse(null, "NONE", null).isEmpty());
        assertTrue(LifeMetrics.parse("", null, null).isEmpty());
    }

    @Test
    void jsonRulesParseAndDropInvalidEntries() {
        String json = "[{\"metric\":\"qty\",\"rated\":50000},{\"metric\":\"QTY\",\"rated\":1},"
                + "{\"metric\":\"DAYS\",\"rated\":0},{\"metric\":\"XXX\",\"rated\":10},"
                + "{\"metric\":\"HOURS\",\"rated\":6000}]";
        List<LifeMetricRule> rules = LifeMetrics.parse(json, null, null);
        assertEquals(2, rules.size(), "重复的 QTY、额定为 0、非法口径都应被丢弃");
        assertEquals("QTY", rules.get(0).metric());
        assertEquals("HOURS", rules.get(1).metric());
        assertEquals(0, rules.get(0).rated().compareTo(new BigDecimal("50000")));
    }

    @Test
    void brokenJsonFallsBackToLegacyFields() {
        List<LifeMetricRule> rules = LifeMetrics.parse("{not json", "TASKS", new BigDecimal("200000"));
        assertEquals(1, rules.size());
        assertEquals("TASKS", rules.get(0).metric());
    }

    @Test
    void strictestMetricWins() {
        // 小时 3100/6000 = 52%，件数 41230/50000 = 82% -> 主导应为件数
        SparePartUsage u = usage(0, 3100, 0, 1000, 120000, 161230);
        List<LifeMetricRule> rules = List.of(
                new LifeMetricRule("HOURS", new BigDecimal("6000")),
                new LifeMetricRule("QTY", new BigDecimal("50000")));

        List<LifeMetricUsageVO> rows = LifeMetrics.evaluate(u, rules);
        assertEquals(2, rows.size());
        assertEquals(52, rows.get(0).usedPercent());
        assertEquals(82, rows.get(1).usedPercent());

        LifeMetricUsageVO top = LifeMetrics.dominant(rows);
        assertNotNull(top);
        assertEquals("QTY", top.metric());
        assertTrue(top.primary());
        assertEquals(0, top.usedLifeValue().compareTo(new BigDecimal("41230")));
        assertEquals(0, top.remainingLifeValue().compareTo(new BigDecimal("8770")));
    }

    @Test
    void daysMetricWorksWithoutDeviceData() {
        SparePartUsage u = usage(0, 0, 0, 0, 0, 0);
        u.setStartedAt(Instant.now().minus(30, ChronoUnit.DAYS));
        List<LifeMetricUsageVO> rows = LifeMetrics.evaluate(u,
                List.of(new LifeMetricRule("DAYS", new BigDecimal("60"))));
        // 用了 30 天 / 额定 60 天 = 50%
        assertEquals(50, rows.get(0).usedPercent());
        assertEquals(0, rows.get(0).usedLifeValue().compareTo(new BigDecimal("30")));
    }

    @Test
    void missingDataLeavesPercentNullInsteadOfZero() {
        SparePartUsage u = new SparePartUsage();
        u.setStartedAt(Instant.now());
        List<LifeMetricUsageVO> rows = LifeMetrics.evaluate(u,
                List.of(new LifeMetricRule("QTY", new BigDecimal("50000"))));
        assertNull(rows.get(0).usedLifeValue());
        assertNull(rows.get(0).usedPercent());
    }

    @Test
    void deviceDataRequirementDetection() {
        assertFalse(LifeMetrics.needsDeviceData(List.of(new LifeMetricRule("DAYS", BigDecimal.TEN))));
        assertTrue(LifeMetrics.needsDeviceData(List.of(new LifeMetricRule("QTY", BigDecimal.TEN))));
        assertFalse(LifeMetrics.needsRuntimeView(List.of(new LifeMetricRule("QTY", BigDecimal.TEN))));
        assertTrue(LifeMetrics.needsPieceQty(List.of(new LifeMetricRule("QTY", BigDecimal.TEN))));
    }

    @Test
    void serializeRejectsNonPositiveRated() {
        assertThrows(IllegalArgumentException.class,
                () -> LifeMetrics.validateAndSerialize(List.of(new LifeMetricRule("HOURS", BigDecimal.ZERO))));
        assertNull(LifeMetrics.validateAndSerialize(List.of()));
        String json = LifeMetrics.validateAndSerialize(List.of(new LifeMetricRule("QTY", new BigDecimal("50000"))));
        assertEquals("[{\"metric\":\"QTY\",\"rated\":50000}]", json);
    }
}
