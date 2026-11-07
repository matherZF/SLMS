package com.wms.controller;

import com.wms.common.Result;
import com.wms.dto.InterfaceLogSummaryVO;
import com.wms.dto.InterfaceLogVO;
import com.wms.service.InterfaceLogService;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/interface-logs")
@PreAuthorize("hasAuthority('system:interface')")
public class InterfaceLogController {
    private final InterfaceLogService service;

    public InterfaceLogController(InterfaceLogService service) { this.service = service; }

    @GetMapping
    public Result<Page<InterfaceLogSummaryVO>> page(@RequestParam(required = false) String direction,
                                                    @RequestParam(required = false) String interfaceCode,
                                                    @RequestParam(required = false) Boolean success,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.page(direction, interfaceCode, success, keyword, page, size));
    }

    @GetMapping("/{id}")
    public Result<InterfaceLogVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }
}
