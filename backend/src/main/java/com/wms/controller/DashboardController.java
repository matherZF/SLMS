package com.wms.controller;

import com.wms.common.Result;
import com.wms.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService service;
    public DashboardController(DashboardService service) { this.service = service; }
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('page:workbench')")
    public Result<Map<String, Object>> summary() { return Result.ok(service.summary()); }
}
