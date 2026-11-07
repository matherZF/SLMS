package com.wms.controller;

import com.wms.common.Result;
import com.wms.dto.PutawayOrderCreateReq;
import com.wms.dto.PutawayOrderDetailVO;
import com.wms.dto.PutawayOrderListItemVO;
import com.wms.service.PutawayOrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class PutawayOrderController {
    private final PutawayOrderService orderService;

    public PutawayOrderController(PutawayOrderService orderService) {
        this.orderService = orderService;
    }

    /** 入库单列表/待上架/详情：入库页与上架作业页共用 */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('page:inbound','inbound:putaway','inbound:create')")
    public Result<Page<PutawayOrderListItemVO>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(orderService.page(status, keyword, page, size));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyAuthority('page:inbound','inbound:putaway','inbound:create')")
    public Result<List<PutawayOrderListItemVO>> pending() {
        return Result.ok(orderService.pendingList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('page:inbound','inbound:putaway','inbound:create')")
    public Result<PutawayOrderDetailVO> detail(@PathVariable Long id) {
        return Result.ok(orderService.detail(id));
    }

    @PreAuthorize("hasAuthority('inbound:create')")
    @PostMapping
    public Result<PutawayOrderDetailVO> create(@Valid @RequestBody PutawayOrderCreateReq req) {
        return Result.ok(orderService.create(req));
    }
}
