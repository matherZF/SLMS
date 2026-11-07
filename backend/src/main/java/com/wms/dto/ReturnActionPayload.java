package com.wms.dto;

public record ReturnActionPayload(Long orderId, Long itemId, Integer qty, String reason) {}
