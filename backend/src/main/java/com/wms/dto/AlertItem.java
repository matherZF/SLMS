package com.wms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一条预警。
 *
 * <p>实时计算与落表共用同一个结构：字段即 {@code wms_spare_part_alert} 的列，
 * 落表时只存这些字段；{@code extra} 是页面展示用、不落表的补充信息
 * （如更换预警的寿命口径与已用百分比）。</p>
 */
public record AlertItem(
        /** 预警对象唯一键：类型 + 备件 + 设备 + 台账 + 库存，不含日期 */
        String dedupKey,
        String alertType,
        String scope,
        Long productId,
        String sku,
        String productName,
        Long equipmentId,
        String equipmentCode,
        String equipmentName,
        Long usageId,
        Long inventoryId,
        String severity,
        Integer stockQty,
        /** 触发阈值：备件级是补货点，更换预警是本次更换需要的量 */
        Integer thresholdQty,
        Integer safetyStock,
        BigDecimal dailyDemand,
        Integer coverageDays,
        LocalDate stockoutDate,
        LocalDate receiveByDate,
        LocalDate orderByDate,
        Integer suggestedQty,
        Integer procurementLeadDays,
        boolean leadEstimated,
        String message,
        Map<String, Object> extra) {

    public AlertItem {
        extra = extra == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extra);
    }

    /** 给页面看的完整字段：落表字段 + 展示补充 */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dedupKey", dedupKey);
        m.put("alertType", alertType);
        m.put("scope", scope);
        m.put("productId", productId);
        m.put("sku", sku);
        m.put("productName", productName);
        m.put("equipmentId", equipmentId);
        m.put("equipmentCode", equipmentCode);
        m.put("equipmentName", equipmentName);
        m.put("usageId", usageId);
        m.put("inventoryId", inventoryId);
        m.put("severity", severity);
        m.put("stockQty", stockQty);
        m.put("thresholdQty", thresholdQty);
        m.put("safetyStock", safetyStock);
        m.put("dailyDemand", dailyDemand);
        m.put("coverageDays", coverageDays);
        m.put("stockoutDate", stockoutDate);
        m.put("receiveByDate", receiveByDate);
        m.put("orderByDate", orderByDate);
        m.put("suggestedQty", suggestedQty);
        m.put("procurementLeadDays", procurementLeadDays);
        m.put("leadEstimated", leadEstimated);
        m.put("message", message);
        m.putAll(extra);
        return m;
    }
}
