package com.wms.dto;

public record LocationDTO(Long id, String code, String area, String type,
                          String warehouseName, String storageMode,
                          Integer rackNo, Integer levelNo, Integer positionNo,
                          Boolean enabled, String deviceNo, String remark,
                          /** 绑定的电子标签 ID（= wms_es_tag.id） */
                          Long esTagId,
                          /** 绑定的电子标签名称（= wms_es_tag.name） */
                          String esTagName) {
}
