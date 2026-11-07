package com.wms.dto;

import java.time.LocalDate;

// 出库明细项视图
public record OutboundItemVO(
        Long id,
        Long productId,
        String sku,
        String name,
        String spec,
        String unit,
        /** 这批备件装到哪台设备；为空表示领用人没指定 */
        Long equipmentId,
        String equipmentCode,
        String equipmentName,
        int requestedQty,
        int pickedQty,
        int returnedQty,
        int returnableQty,
        Long locationId,
        String locationCode,
        String locationArea,
        String deviceNo,
        int availableQty,
        // 批次信息
        Long batchId,
        String itemKey,
        LocalDate productionDate,
        Integer shelfLifeDays,
        String manufacturer,
        LocalDate expiryDate,
        boolean scanVerified,
        /** 电子标签右下按键已确认 —— completePicking() 必要条件 */
        boolean buttonPressed,
        java.time.Instant buttonPressedAt,
        /** 亮灯结果回调：null=未收到回调，1=result=true，2=result=false */
        Integer lightConfirmed,
        java.time.Instant lightConfirmedAt,
        String lightMessage
) {}
