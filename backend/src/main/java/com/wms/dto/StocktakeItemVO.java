package com.wms.dto;

public record StocktakeItemVO(Long id, Long inventoryId, String sku, String productName,
                              String locationCode, String itemKey, int bookQty,
                              Integer actualQty, Integer differenceQty) {}
