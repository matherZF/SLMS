package com.wms.dto;

/**
 * 上架确认请求。
 *
 * @param orderId    上架单 ID
 * @param locationId 目标库位 ID
 * @param qty        本次上架数量
 * @param requestId  幂等键（可选）：前端每次点击生成一个 UUID。
 *                   网络重试 / 双击时带上同一个值，服务端据此识别重放，不会重复入库；
 *                   不传则退化为"每次请求都算一次新的上架"。
 */
public record PutawayConfirmReq(Long orderId, Long locationId, Integer qty, String requestId) {
}
