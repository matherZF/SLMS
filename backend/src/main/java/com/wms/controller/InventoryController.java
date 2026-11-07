package com.wms.controller;

import com.wms.common.Result;
import com.wms.dto.InventoryDetailVO;
import com.wms.dto.InventoryAdjustmentReq;
import com.wms.dto.InventoryAdjustmentVO;
import com.wms.dto.InventoryTransferReq;
import com.wms.dto.InventoryTransferVO;
import com.wms.dto.InventorySummaryVO;
import com.wms.service.InventoryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * 库存汇总 / 明细：库存页、领用单选货、工作台统计三处会用，
     * 因此放行这三类页面权限；只读用户（page:inventory）即可查看，不再需要额外按钮权限。
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('page:inventory','page:outbound','page:workbench')")
    public Result<List<InventorySummaryVO>> summary(@RequestParam(required = false) String sku) {
        return Result.ok(inventoryService.summary(sku));
    }

    @GetMapping("/details")
    @PreAuthorize("hasAnyAuthority('page:inventory','page:outbound','page:workbench')")
    public Result<List<InventoryDetailVO>> details(
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String locationCode) {
        return Result.ok(inventoryService.details(sku, locationCode));
    }

    @PreAuthorize("hasAuthority('inventory:scrap')")
    @PostMapping("/adjustments")
    public Result<InventoryAdjustmentVO> adjust(@Valid @RequestBody InventoryAdjustmentReq req) {
        return Result.ok(inventoryService.adjust(req));
    }

    @PreAuthorize("hasAuthority('inventory:transfer')")
    @PostMapping("/transfers")
    public Result<InventoryTransferVO> transfer(@Valid @RequestBody InventoryTransferReq req) {
        return Result.ok(inventoryService.transfer(req));
    }
}
