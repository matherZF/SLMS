package com.wms.controller;

import com.wms.common.Result;
import com.wms.dto.AvailableStockVO;
import com.wms.dto.OutboundCreateReq;
import com.wms.dto.OutboundItemVO;
import com.wms.dto.OutboundOrderDetailVO;
import com.wms.dto.OutboundOrderListItemVO;
import com.wms.dto.OutboundScanVerifyReq;
import com.wms.dto.OutboundReturnReq;
import com.wms.service.OutboundService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/outbound")
public class OutboundController {
    private final OutboundService outboundService;

    public OutboundController(OutboundService outboundService) {
        this.outboundService = outboundService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('page:outbound','page:approval','outbound:manage')")
    public Result<Page<OutboundOrderListItemVO>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(outboundService.page(status, keyword, page, size));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('outbound:picking')")
    public Result<List<OutboundOrderListItemVO>> pending() {
        return Result.ok(outboundService.pendingList());
    }

    /** 详情被领用单详情页与拣货页共用，因此两个方向的权限都放行 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('page:outbound','outbound:picking','page:approval','outbound:manage')")
    public Result<OutboundOrderDetailVO> detail(@PathVariable Long id) {
        return Result.ok(outboundService.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('outbound:create')")
    public Result<OutboundOrderDetailVO> create(@Valid @RequestBody OutboundCreateReq req) {
        return Result.ok(outboundService.create(req));
    }

    /** 查询某货品的可用库存（含批次详情），用于人工选择库位/批次 */
    @GetMapping("/available-stock")
    @PreAuthorize("hasAuthority('outbound:picking')")
    public Result<List<AvailableStockVO>> availableStock(@RequestParam Long productId) {
        return Result.ok(outboundService.listAvailableInventory(productId));
    }

    /** 人工为出库项分配库位+批次 */
    @PostMapping("/{orderId}/items/{itemId}/assign")
    @PreAuthorize("hasAuthority('outbound:picking')")
    public Result<OutboundItemVO> assignLocation(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @RequestParam Long locationId,
            @RequestParam(required = false) Long batchId) {
        return Result.ok(outboundService.assignLocation(orderId, itemId, locationId, batchId));
    }

    /** 开始拣货：分配库位 + 触发灯光设备 */
    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('outbound:picking')")
    public Result<Map<String, Object>> start(@PathVariable Long id) {
        return Result.ok(outboundService.startPicking(id));
    }
    @GetMapping("/approval-tasks") @PreAuthorize("hasAuthority('outbound:approve')")
    public Result<List<OutboundOrderListItemVO>> approvalTasks(){return Result.ok(outboundService.approvalTasks());}

    /** 出库最终确认前，核验备件二维码和货位二维码。 */
    @PostMapping("/{id}/scan-verify")
    @PreAuthorize("hasAuthority('outbound:picking')")
    public Result<Map<String, Object>> verifyScan(
            @PathVariable Long id,
            @Valid @RequestBody OutboundScanVerifyReq req) {
        return Result.ok(outboundService.verifyScan(
                id, req.itemId(), req.partCode(), req.locationCode()));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('outbound:approve')")
    public Result<Map<String, Object>> approve(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        return Result.ok(outboundService.review(id, true, body == null ? null : body.get("opinion")));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('outbound:approve')")
    public Result<Map<String, Object>> reject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        return Result.ok(outboundService.review(id, false, body == null ? null : body.get("opinion")));
    }

    @PostMapping("/{id}/resubmit")
    @PreAuthorize("hasAuthority('outbound:create')")
    public Result<Map<String, Object>> resubmit(@PathVariable Long id) {
        return Result.ok(outboundService.resubmit(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('page:outbound','outbound:manage')")
    public Result<Map<String, Object>> cancel(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        return Result.ok(outboundService.cancel(id, body == null ? null : body.get("reason")));
    }

    /** 拣货完成：扣减库存 */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('outbound:picking')")
    public Result<Map<String, Object>> complete(
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return Result.ok(outboundService.completePicking(id, idempotencyKey));
    }

    /** 从已完成领用单的具体明细发起退库。 */
    @PostMapping("/{id}/return")
    @PreAuthorize("hasAnyAuthority('page:outbound','outbound:manage')")
    public Result<Map<String, Object>> returnItem(
            @PathVariable Long id,
            @Valid @RequestBody OutboundReturnReq req) {
        return Result.ok(outboundService.returnItem(id, req));
    }
}
