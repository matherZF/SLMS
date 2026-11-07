package com.wms.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// 出库单项创建请求
public record OutboundItemCreateReq(
        @NotNull Long productId,
        @NotNull @Min(1) Integer requestedQty,
        /**
         * 这批备件装到哪台设备。一张单可以有多条明细、各装不同设备，所以设备挂在明细上。
         * 留空 = 领用人没指定，台账建档时退回备件在基础数据里的唯一关联。
         */
        Long equipmentId
) {}
