package com.wms.controller;

import com.wms.common.Result;
import com.wms.dto.SparePartUsageSnapshotVO;
import com.wms.dto.SparePartUsageVO;
import com.wms.service.SparePartUsageService;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/spare-part-usages")
public class SparePartUsageController {

    private final SparePartUsageService service;

    public SparePartUsageController(SparePartUsageService s) { this.service = s; }

    /** 台账查看属于 page:usage；领用单详情里也会带出台账摘要，但那走的是出库接口，不在这里 */
    @GetMapping
    @PreAuthorize("hasAuthority('page:usage')")
    public Result<Page<SparePartUsageVO>> page(@RequestParam(required = false) String status,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.page(status, keyword, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('page:usage')")
    public Result<SparePartUsageVO> detail(@PathVariable Long id) { return Result.ok(service.detail(id)); }

    @GetMapping("/{id}/snapshots")
    @PreAuthorize("hasAuthority('page:usage')")
    public Result<List<SparePartUsageSnapshotVO>> snapshots(@PathVariable Long id) { return Result.ok(service.snapshots(id)); }

    @PreAuthorize("hasAnyAuthority('outbound:manage','outbound:picking')")
    @PostMapping("/{id}/refresh")
    public Result<SparePartUsageVO> refresh(@PathVariable Long id) { return Result.ok(service.refresh(id)); }

    @PreAuthorize("hasAnyAuthority('outbound:manage','outbound:picking')")
    @PostMapping("/{id}/end")
    public Result<SparePartUsageVO> end(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.ok(service.end(id, body == null ? null : body.get("reason")));
    }

    @PreAuthorize("hasAnyAuthority('outbound:manage','outbound:picking')")
    @PostMapping("/refresh-all")
    public Result<Map<String, Integer>> refreshAll() { return Result.ok(service.refreshAll("MANUAL")); }
}
