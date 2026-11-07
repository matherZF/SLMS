package com.wms.controller;

import com.wms.common.Result;
import com.wms.dto.SystemConfigDTO;
import com.wms.service.SystemConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system-configs")
public class SystemConfigController {
    private final SystemConfigService service;

    public SystemConfigController(SystemConfigService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('system:config')")
    public Result<List<SystemConfigDTO>> list() { return Result.ok(service.list()); }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:config')")
    public Result<SystemConfigDTO> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.ok(service.update(id, body.get("configValue")));
    }

    @PostMapping("/{id}/reset")
    @PreAuthorize("hasAuthority('system:config')")
    public Result<SystemConfigDTO> reset(@PathVariable Long id) { return Result.ok(service.reset(id)); }
}
