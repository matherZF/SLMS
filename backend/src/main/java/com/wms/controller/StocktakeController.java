package com.wms.controller;

import com.wms.common.Result;
import com.wms.dto.*;
import com.wms.service.StocktakeService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stocktakes")
public class StocktakeController {
    private final StocktakeService service;
    public StocktakeController(StocktakeService service) { this.service = service; }

    /** 盘点列表/详情：盘点页，以及审批人复核差异时查看单据 */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('page:stocktake','page:approval')")
    public Result<Page<StocktakeOrderVO>> page(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.page(page, size));
    }
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('page:stocktake','page:approval')")
    public Result<StocktakeOrderVO> detail(@PathVariable Long id) { return Result.ok(service.detail(id)); }
    @PostMapping
    @PreAuthorize("hasAuthority('stocktake:create')")
    public Result<StocktakeOrderVO> create(@RequestBody(required = false) StocktakeCreateReq req) {
        return Result.ok(service.create(req));
    }
    @PutMapping("/{id}/count")
    @PreAuthorize("hasAuthority('stocktake:complete')")
    public Result<StocktakeOrderVO> count(@PathVariable Long id, @Valid @RequestBody StocktakeCountReq req) {
        return Result.ok(service.count(id, req));
    }
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('stocktake:complete')")
    public Result<StocktakeOrderVO> complete(@PathVariable Long id) { return Result.ok(service.complete(id)); }
}
