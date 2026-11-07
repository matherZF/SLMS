package com.wms.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 单个计量口径的实时使用情况（用于前端展示"哪个口径先到"）。
 * primary = true 表示这是当前最严的口径（百分比最高），寿命状态按它判定。
 *
 * <p>后三个字段是预测信息：{@code dailyRate} 为按历史快照拟合出的日均消耗速度，
 * {@code rateSampleDays} 为拟合所用样本的时间跨度（天），{@code expectedEndAt} 为
 * 按当前速度把剩余寿命耗尽的日期（DAYS 口径直接由开始使用时间加额定天数得出）。
 * 样本跨度不足时不产出速率，三个字段留 null，前端据此提示"数据累积中"。</p>
 */
public record LifeMetricUsageVO(
        String metric,
        BigDecimal ratedLifeValue,
        BigDecimal usedLifeValue,
        BigDecimal remainingLifeValue,
        Integer usedPercent,
        boolean primary,
        BigDecimal dailyRate,
        Integer rateSampleDays,
        Instant expectedEndAt) {

    /** 无预测信息的构造方式，供不涉及速率的调用方（列表、导出、告警）使用 */
    public LifeMetricUsageVO(String metric, BigDecimal ratedLifeValue, BigDecimal usedLifeValue,
                             BigDecimal remainingLifeValue, Integer usedPercent, boolean primary) {
        this(metric, ratedLifeValue, usedLifeValue, remainingLifeValue, usedPercent, primary, null, null, null);
    }

    public LifeMetricUsageVO withPrimary(boolean value) {
        return new LifeMetricUsageVO(metric, ratedLifeValue, usedLifeValue, remainingLifeValue,
                usedPercent, value, dailyRate, rateSampleDays, expectedEndAt);
    }
}
