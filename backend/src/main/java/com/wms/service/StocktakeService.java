package com.wms.service;

import com.wms.common.BizException;
import com.wms.dto.*;
import com.wms.entity.*;
import com.wms.repository.InventoryRepository;
import com.wms.repository.StocktakeOrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class StocktakeService {
    private final StocktakeOrderRepository orderRepo;
    private final InventoryRepository inventoryRepo;
    private final InventoryLogService logService;
    private final OperationAuditService auditService;
    private final PendingBusinessActionService pendingActions;
    private final NumberSequenceService numberSequence;

    public StocktakeService(StocktakeOrderRepository orderRepo, InventoryRepository inventoryRepo,
                            InventoryLogService logService, OperationAuditService auditService,
                            PendingBusinessActionService pendingActions, NumberSequenceService numberSequence) {
        this.orderRepo = orderRepo;
        this.inventoryRepo = inventoryRepo;
        this.logService = logService;
        this.auditService = auditService;
        this.pendingActions = pendingActions;
        this.numberSequence = numberSequence;
    }

    @Transactional
    public StocktakeOrderVO create(StocktakeCreateReq req) {
        List<Inventory> inventories = inventoryRepo.findAllWithGraph();
        if (inventories.isEmpty()) throw new BizException("当前没有可盘点的库存记录");
        StocktakeOrder order = new StocktakeOrder();
        order.setOrderNo(numberSequence.nextOrderNo("ST"));
        order.setStatus(StocktakeStatus.COUNTING);
        order.setRemark(req == null ? null : req.remark());
        order.setCreatedBy(operator());
        for (Inventory inventory : inventories) {
            StocktakeItem item = new StocktakeItem();
            item.setOrder(order);
            item.setInventory(inventory);
            item.setBookQty(inventory.getQty());
            order.getItems().add(item);
        }
        orderRepo.save(order);
        auditService.record("STOCKTAKE", order.getId(), order.getOrderNo(), "CREATE", null,
                StocktakeStatus.COUNTING.name(), "创建盘点单，共 " + order.getItems().size() + " 条库存");
        return toVO(order, true);
    }

    @Transactional(readOnly = true)
    public Page<StocktakeOrderVO> page(int page, int size) {
        return orderRepo.findAllByOrderByIdDesc(PageRequest.of(page, size)).map(o -> toVO(o, false));
    }

    @Transactional(readOnly = true)
    public StocktakeOrderVO detail(Long id) {
        return toVO(orderRepo.findDetail(id).orElseThrow(() -> new BizException("盘点单不存在")), true);
    }

    @Transactional
    public StocktakeOrderVO count(Long id, StocktakeCountReq req) {
        StocktakeOrder order = editableOrder(id);
        StocktakeItem item = order.getItems().stream()
                .filter(i -> Objects.equals(i.getId(), req.itemId())).findFirst()
                .orElseThrow(() -> new BizException("盘点明细不属于当前盘点单"));
        item.setActualQty(req.actualQty());
        auditService.record("STOCKTAKE", order.getId(), order.getOrderNo(), "COUNT", order.getStatus().name(),
                order.getStatus().name(), "录入 " + item.getInventory().getProduct().getSku() + " 实盘数量：" + req.actualQty());
        return toVO(orderRepo.save(order), true);
    }

    @Transactional(rollbackFor = Exception.class)
    public StocktakeOrderVO complete(Long id) {
        StocktakeOrder order = editableOrder(id);
        if (order.getItems().stream().anyMatch(i -> i.getActualQty() == null)) {
            throw new BizException("仍有库存明细尚未录入实盘数量");
        }
        long differences = order.getItems().stream().filter(i -> !Objects.equals(i.getActualQty(), i.getBookQty())).count();
        if (differences > 0) {
            StocktakeStatus previousStatus = order.getStatus();
            order.setStatus(StocktakeStatus.PENDING_REVIEW);
            orderRepo.save(order);
            var submission = pendingActions.submit("STOCKTAKE_DIFF", order.getId(), "SD", operator(), java.util.Map.of("orderId", order.getId())); var action=submission.action();
            if(submission.completed()){executeApprovedDifference(order.getId(),operator());pendingActions.finish(action,"APPROVED");return toVO(orderRepo.findDetail(order.getId()).orElse(order),true);}
            auditService.record("STOCKTAKE", order.getId(), order.getOrderNo(), "SUBMIT_REVIEW",
                    previousStatus.name(), order.getStatus().name(), operator(),
                    "提交盘点差异复核：" + action.getBusinessNo() + "；差异项：" + differences);
            return toVO(order, true);
        }
        applyDifferences(order, operator());
        return toVO(order, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public void executeApprovedDifference(Long id, String currentOperator) {
        StocktakeOrder order = orderRepo.findDetailForUpdate(id).orElseThrow(() -> new BizException("盘点单不存在"));
        if (order.getStatus() != StocktakeStatus.PENDING_REVIEW) throw new BizException("盘点单不在差异复核状态");
        applyDifferences(order, currentOperator);
    }

    @Transactional
    public void rejectReview(Long id, String currentOperator) {
        StocktakeOrder order = orderRepo.findDetailForUpdate(id).orElseThrow(() -> new BizException("盘点单不存在"));
        if (order.getStatus() != StocktakeStatus.PENDING_REVIEW) return;
        order.setStatus(StocktakeStatus.COUNTING);
        orderRepo.save(order);
        auditService.record("STOCKTAKE", order.getId(), order.getOrderNo(), "REVIEW_REJECT",
                StocktakeStatus.PENDING_REVIEW.name(), StocktakeStatus.COUNTING.name(), currentOperator, "盘点差异复核驳回，可重新核对并提交");
    }

    private void applyDifferences(StocktakeOrder order, String currentOperator) {
        for (StocktakeItem item : order.getItems()) {
            // 按"差异量"调整，而不是用实盘数直接覆盖库存。
            // 盘点单创建时把当时的账面数快照进了 bookQty；从建单到复核通过这段时间里，
            // 库位可能又发生了出入库。直接 setQty(actual) 会把这些真实发生的变动一并抹掉，
            // 造出一批"盘点完就凭空多出来/少掉"的幽灵库存。
            int delta = item.getActualQty() - item.getBookQty();
            if (delta == 0) continue;
            Inventory inventory = inventoryRepo.findWithGraphForUpdate(item.getInventory().getId())
                    .orElseThrow(() -> new BizException("盘点库存记录不存在"));
            int before = inventory.getQty();
            int after = before + delta;
            if (after < 0) {
                throw new BizException(String.format(
                        "差异调整后库存不能为负：库位 %s 当前 %d，差异 %+d",
                        inventory.getLocation().getCode(), before, delta));
            }
            if (after < inventory.getReservedQty()) {
                throw new BizException("盘点实数不能低于拣货占用数：" + inventory.getLocation().getCode()
                        + " 当前占用 " + inventory.getReservedQty());
            }
            inventory.setQty(after);
            inventoryRepo.save(inventory);
            Batch batch = inventory.getBatch();
            logService.record(inventory.getProduct().getId(), inventory.getProduct().getSku(),
                    inventory.getProduct().getName(), inventory.getLocation().getId(),
                    inventory.getLocation().getCode(), "ADJUST", Math.abs(delta), before, after,
                    "STOCKTAKE", order.getOrderNo(), currentOperator,
                    String.format("盘点差异调整，账面快照：%d，实盘：%d，差异：%+d",
                            item.getBookQty(), item.getActualQty(), delta),
                    batch != null ? batch.getItemKey() : null,
                    batch != null ? batch.getProductionDate() : null,
                    batch != null ? batch.getShelfLifeDays() : null,
                    batch != null ? batch.getManufacturer() : null);
        }
        StocktakeStatus previousStatus = order.getStatus();
        order.setStatus(StocktakeStatus.DONE);
        order.setCompletedBy(currentOperator);
        order.setCompletedAt(Instant.now());
        orderRepo.save(order);
        long differences = order.getItems().stream().filter(i -> !Objects.equals(i.getActualQty(), i.getBookQty())).count();
        auditService.record("STOCKTAKE", order.getId(), order.getOrderNo(), "COMPLETE",
                previousStatus.name(), order.getStatus().name(), "完成盘点；差异项：" + differences);
    }

    private StocktakeOrder editableOrder(Long id) {
        StocktakeOrder order = orderRepo.findDetailForUpdate(id)
                .orElseThrow(() -> new BizException("盘点单不存在"));
        if (order.getStatus() != StocktakeStatus.COUNTING) throw new BizException("该盘点单已结束，不能修改");
        return order;
    }

    private StocktakeOrderVO toVO(StocktakeOrder order, boolean includeItems) {
        int counted = (int) order.getItems().stream().filter(i -> i.getActualQty() != null).count();
        List<StocktakeItemVO> items = includeItems ? order.getItems().stream().map(i -> {
            Inventory inv = i.getInventory();
            Batch batch = inv.getBatch();
            Integer difference = i.getActualQty() == null ? null : i.getActualQty() - i.getBookQty();
            return new StocktakeItemVO(i.getId(), inv.getId(), inv.getProduct().getSku(),
                    inv.getProduct().getName(), inv.getLocation().getCode(),
                    batch == null ? null : batch.getItemKey(), i.getBookQty(), i.getActualQty(), difference);
        }).toList() : null;
        return new StocktakeOrderVO(order.getId(), order.getOrderNo(), order.getStatus().name(), order.getRemark(),
                order.getCreatedBy(), order.getCreatedAt(), order.getCompletedBy(), order.getCompletedAt(),
                order.getItems().size(), counted, items);
    }

    private String operator() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "system" : String.valueOf(auth.getPrincipal());
    }
}
