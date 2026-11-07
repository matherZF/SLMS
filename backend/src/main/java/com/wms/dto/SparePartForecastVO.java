package com.wms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 备件需求预测。
 *
 * <p>回答采购最关心的三件事：<b>什么时候买</b>、<b>什么时候必须到货</b>、<b>这一单最少买多少</b>。
 * 三个日期的含义不同，不能混：</p>
 * <pre>
 * stockoutDate   库存按当前日耗见底的日子
 * receiveByDate  最迟到货日 = 见底日 − 安全天数（留出缓冲，不能掐着见底那天收货）
 * orderByDate    最晚采购日 = 最迟到货日 − 采购周期
 * </pre>
 *
 * <p>没填采购周期时 {@code orderByDate} 仍会给出，但 {@code leadEstimated=true}，
 * 页面必须标明这是按默认值估算的——采购周期是「 supplier 真实交期 」，估错会导致货到晚了。</p>
 *
 * <p>{@code dailyDemand} 是「件/天」，由设备运行强度（产量 / 运行时长 / 任务数）与备件寿命推算，
 * 不是历史出库量的平均——出库量反映的是"过去补了几次"，会被紧急领用和批量采购扭曲；
 * 运行强度反映的是"设备到底磨掉了多少"，与库存无关，更接近真实需求。</p>
 *
 * <p>{@code actionHint} 是给人看的一句话结论；参数不足导致的缺口写在 {@code gaps} 里，
 * 宁可写明缺哪个参数，也不给一个看似合理但会误导采购的数。</p>
 */
public record SparePartForecastVO(
        Long productId,
        String sku,
        String productName,
        String unit,
        /** READY / NO_DEVICE / NO_PRODUCTION / MISSING_LIFE */
        String status,
        String statusText,
        /** 备件级日均需求（件/天） */
        BigDecimal dailyDemand,
        /** 当前可用库存 */
        Integer stockQty,
        /** 按当前日耗还能撑多少天 */
        Integer coverageDays,
        /** 预计缺货日 */
        LocalDate stockoutDate,
        /** 最迟到货日 = 缺货日 − 安全天数；货必须在这一天之前到，否则会断料 */
        LocalDate receiveByDate,
        /** 最晚采购（下单）日 = 最迟到货日 − 采购周期 */
        LocalDate orderByDate,
        /** true 表示没填采购周期，orderByDate 是按系统默认值估算的 */
        boolean leadEstimated,
        /** 采购周期（天）；未填为 null */
        Integer leadDays,
        /** 计划保留的安全库存 */
        int safetyStock,
        /** 建议补货量（至少补多少） */
        Integer suggestedQty,
        /** 一句话行动建议，直接给采购看 */
        String actionHint,
        /** HIGH / MEDIUM / null */
        String severity,
        /** 各设备的需求贡献 */
        List<DeviceDemand> devices,
        /** 还缺哪些参数才能预测 */
        List<String> gaps) {

    /**
     * 单台设备上该备件的日耗及其依据。
     *
     * <p>三个设备侧口径彼此独立：源表可能只给产量、也可能只给运行时长（立项素材里
     * {@code SUM_HOURS} 就是部分设备才有）。哪个口径有数据、备件又配了对应的额定值，
     * 哪个才参与计算。</p>
     *
     * @param avgDailyHours  该设备日均运行小时；源表没给时为 null
     * @param avgDailyTasks  该设备日均任务次数；源表没给时为 null
     * @param avgDailyOutput 该设备日均处理件数；源表没给时为 null
     * @param basis          这条日耗是哪个口径算出来的，便于现场核对
     */
    public record DeviceDemand(
            Long equipmentId,
            String equipmentCode,
            String equipmentName,
            int installedQty,
            BigDecimal avgDailyHours,
            BigDecimal avgDailyTasks,
            BigDecimal avgDailyOutput,
            BigDecimal dailyDemand,
            String basis) {
    }
}
