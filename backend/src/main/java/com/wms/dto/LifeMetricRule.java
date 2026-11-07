package com.wms.dto;

import java.math.BigDecimal;

/**
 * 一条寿命计量规则。
 * metric：HOURS 运行小时 / TASKS 任务次数 / QTY 处理件数 / DAYS 自然使用天数
 * rated：该口径下的额定寿命（单位随 metric 变化，各口径独立）
 */
public record LifeMetricRule(String metric, BigDecimal rated) {
}
