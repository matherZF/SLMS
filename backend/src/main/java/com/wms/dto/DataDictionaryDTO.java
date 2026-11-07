package com.wms.dto;

import java.time.Instant;

public record DataDictionaryDTO(Long id, String dictType, String itemCode, String itemLabel,
                                Integer sortOrder, Boolean enabled, String remark,
                                Instant createdAt, Instant updatedAt) {}
