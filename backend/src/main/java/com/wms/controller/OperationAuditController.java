package com.wms.controller;

import com.wms.common.Result;
import com.wms.dto.OperationAuditLogVO;
import com.wms.service.OperationAuditService;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit-logs")
@PreAuthorize("hasAuthority('system:audit')")
public class OperationAuditController {
    private final OperationAuditService service;
    public OperationAuditController(OperationAuditService service) { this.service = service; }

    @GetMapping
    public Result<Page<OperationAuditLogVO>> page(@RequestParam(required = false) String businessType,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.page(businessType, keyword, page, size));
    }
}
