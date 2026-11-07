package com.wms.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record PutawayOrderCreateReq(
        @NotNull(message = "productId不能为空") Long productId,
        @NotNull(message = "planQty不能为空")
        @Min(value = 1, message = "planQty必须大于0") Integer planQty,
        /** 批次号（唯一，可留空由系统生成） */
        String itemKey,
        /** 生产日期 */
        LocalDate productionDate,
        /** 保质期（天） */
        Integer shelfLifeDays,
        /** 生产厂商 */
        String manufacturer
) {
}
