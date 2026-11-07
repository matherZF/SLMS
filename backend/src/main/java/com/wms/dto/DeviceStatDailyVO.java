package com.wms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DeviceStatDailyVO(Long id, LocalDate statDate, String sourceSys, String deviceType,
                                String deviceCode, BigDecimal runningHours, Long taskCount, Long totalQty) {
}
