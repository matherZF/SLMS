package com.wms.dto;

import java.time.Instant;

public record ProductDTO(
        Long id, String sku, String name, String spec, String unit,
        String category, String brand, String model, String drawingNo, String manufacturer,
        String criticality, Boolean vulnerable, Boolean batchManaged, Boolean serialManaged,
        Integer defaultShelfLifeDays, Integer minStock, Integer maxStock, Integer safetyStock,
        Integer procurementLeadDays, Boolean enabled, String remark,
        String lifeMetric, java.math.BigDecimal ratedLifeValue, Integer lifeWarningPercent, Integer lifeWarningDays, java.math.BigDecimal referenceUnitPrice,
        java.util.List<LifeMetricRule> lifeMetricRules,
        Instant createdAt, Instant updatedAt
) {}
