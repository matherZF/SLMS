package com.wms.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.time.LocalDate;

// 出库单创建请求：包含多个拣货项
// 使用设备由领用人在每条明细上指定：大部分备件是通用件、同时挂在多台设备上，系统无法判断这批装到哪台。
// 而且一张单可以有多个备件、装在不同设备上，所以设备只能挂在明细，不能挂在单头。
public record OutboundCreateReq(
        @NotEmpty List<OutboundItemCreateReq> items,
        @NotBlank String purpose,
        // 领用部门为选填：现场有的领用场景归不到具体部门，强制必填会逼用户随便填一个，反而污染统计
        String department,
        @NotBlank String recipient,
        String contactPhone,
        String workOrderNo,
        LocalDate requiredDate,
        String deliveryLocation
) {}
