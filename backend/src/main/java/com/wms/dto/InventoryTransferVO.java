package com.wms.dto;

public record InventoryTransferVO(String transferNo, Long sourceInventoryId, Long targetInventoryId,
                                  String sourceLocationCode, String targetLocationCode, Integer qty) {}
