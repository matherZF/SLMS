package com.wms.service;

import com.wms.common.BizException;
import com.wms.dto.InventoryAdjustmentReq;
import com.wms.dto.InventoryAdjustmentVO;
import com.wms.dto.InventoryTransferReq;
import com.wms.dto.InventoryTransferVO;
import com.wms.dto.InventoryDetailVO;
import com.wms.dto.InventorySummaryVO;
import com.wms.entity.Batch;
import com.wms.entity.Inventory;
import com.wms.entity.Location;
import com.wms.repository.InventoryRepository;
import com.wms.repository.LocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class InventoryService {
    private final InventoryRepository inventoryRepo;
    private final InventoryLogService inventoryLogService;
    private final LocationRepository locationRepo;
    private final OperationAuditService auditService;
    private final PendingBusinessActionService pendingActions;

    public InventoryService(InventoryRepository inventoryRepo, InventoryLogService inventoryLogService,
                            LocationRepository locationRepo, OperationAuditService auditService,
                            PendingBusinessActionService pendingActions) {
        this.inventoryRepo = inventoryRepo;
        this.inventoryLogService = inventoryLogService;
        this.locationRepo = locationRepo;
        this.auditService = auditService;
        this.pendingActions = pendingActions;
    }

    /** 按货品汇总；实存为 0 的货品不出现在结果中 */
    public List<InventorySummaryVO> summary(String skuKeyword) {
        List<Inventory> list = inventoryRepo.findAllWithGraph();
        String kw = StringUtils.hasText(skuKeyword) ? skuKeyword.trim().toLowerCase() : "";
        Map<Long, InventorySummaryVO> map = new LinkedHashMap<>();
        for (Inventory inv : list) {
            var p = inv.getProduct();
            if (!kw.isEmpty()) {
                boolean m1 = p.getSku() != null && p.getSku().toLowerCase().contains(kw);
                boolean m2 = p.getName() != null && p.getName().toLowerCase().contains(kw);
                if (!m1 && !m2) continue;
            }
            InventorySummaryVO s = map.get(p.getId());
            int addQty = inv.getQty();
            int addReserved = inv.getReservedQty();
            if (s == null) {
                s = new InventorySummaryVO(p.getId(), p.getSku(), p.getName(), p.getSpec(), p.getUnit(),
                        addQty, addReserved, addQty - addReserved);
            } else {
                s = new InventorySummaryVO(s.productId(), s.sku(), s.name(), s.spec(), s.unit(),
                        s.totalQty() + addQty, s.reservedQty() + addReserved,
                        s.availableQty() + addQty - addReserved);
            }
            map.put(p.getId(), s);
        }
        // 实存为 0 的货品不展示（全部库位都空时不占列表）
        return map.values().stream().filter(s -> s.totalQty() > 0).collect(Collectors.toList());
    }

    /** 按库位明细；实存为 0 的库存记录不展示 */
    public List<InventoryDetailVO> details(String sku, String locationCode) {
        List<Inventory> list = inventoryRepo.findAllWithGraph();
        String kw = StringUtils.hasText(sku) ? sku.trim().toLowerCase() : "";
        String locKw = StringUtils.hasText(locationCode) ? locationCode.trim().toLowerCase() : "";
        return list.stream().filter(inv -> {
            if (inv.getQty() <= 0) return false;
            boolean ok1 = true, ok2 = true;
            if (!kw.isEmpty()) {
                boolean m1 = inv.getProduct().getSku() != null && inv.getProduct().getSku().toLowerCase().contains(kw);
                boolean m2 = inv.getProduct().getName() != null && inv.getProduct().getName().toLowerCase().contains(kw);
                ok1 = m1 || m2;
            }
            if (!locKw.isEmpty()) {
                ok2 = inv.getLocation().getCode() != null && inv.getLocation().getCode().toLowerCase().contains(locKw);
            }
            return ok1 && ok2;
        }).sorted(Comparator.comparing((Inventory a) -> a.getProduct().getSku())
                .thenComparing(a -> a.getLocation().getCode()))
                .map(inv -> {
                    Batch b = inv.getBatch();
                    return new InventoryDetailVO(
                            inv.getId(),
                            inv.getProduct().getId(),
                            inv.getProduct().getSku(),
                            inv.getProduct().getName(),
                            inv.getLocation().getId(),
                            inv.getLocation().getCode(),
                            inv.getLocation().getArea(),
                            inv.getQty(),
                            inv.getReservedQty(),
                            inv.getAvailableQty(),
                            inv.getUpdatedAt(),
                            b != null ? b.getId() : null,
                            b != null ? b.getItemKey() : null,
                            b != null ? b.getProductionDate() : null,
                            b != null ? b.getShelfLifeDays() : null,
                            b != null ? b.getManufacturer() : null,
                            b != null ? b.getExpiryDate() : null
                    );
                }).collect(Collectors.toList());
    }

    /**
     * 报废扣减指定库存批次；库存与流水在同一事务中提交。
     */
    @Transactional
    public InventoryAdjustmentVO adjust(InventoryAdjustmentReq req) {
        String type = req.type().trim().toUpperCase(Locale.ROOT);
        if (!"SCRAP".equals(type)) {
            throw new BizException("库存页面仅支持报废；退库请从已完成领用单发起");
        }
        String reason = req.reason().trim();
        if (reason.length() > 180) {
            throw new BizException("原因不能超过180个字符");
        }

        Inventory inventory = inventoryRepo.findWithGraphById(req.inventoryId())
                .orElseThrow(() -> new BizException("库存记录不存在"));
        int beforeQty = inventory.getQty();
        if (req.qty() > inventory.getAvailableQty()) {
            throw new BizException("报废数量不能超过可用库存 " + inventory.getAvailableQty());
        }
        String operator = currentOperator();
        var submission = pendingActions.submit("SCRAP", inventory.getId(), "SC", operator, req); var action=submission.action();
        if(submission.completed()){executeApprovedScrap(req,operator);pendingActions.finish(action,"APPROVED");}
        auditService.record("INVENTORY", inventory.getId(), action.getBusinessNo(), "SCRAP_SUBMIT", null, null,
                operator, "提交报废审批；备件 " + inventory.getProduct().getSku() + "，数量 " + req.qty() + "；原因：" + reason);
        // 自动放行时库存在上面已经扣过了，直接读最新值；走审批时先给出"预期结果"
        int afterQty = submission.completed() ? inventory.getQty() : beforeQty - req.qty();
        return new InventoryAdjustmentVO(action.getBusinessNo(), type, inventory.getId(), beforeQty, req.qty(), afterQty,
                operator, java.time.Instant.now());
    }

    /** 最终审批通过后执行报废扣减，并再次校验库存。 */
    @Transactional(rollbackFor = Exception.class)
    public void executeApprovedScrap(InventoryAdjustmentReq req, String operator) {
        Inventory inventory = inventoryRepo.findWithGraphForUpdate(req.inventoryId())
                .orElseThrow(() -> new BizException("库存记录不存在"));
        int beforeQty = inventory.getQty();
        if (req.qty() > inventory.getAvailableQty()) throw new BizException("审批执行失败：当前可用库存仅剩 " + inventory.getAvailableQty());
        int afterQty = beforeQty - req.qty();
        inventory.setQty(afterQty);
        inventoryRepo.save(inventory);
        String refNo = "SC" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        String reason = req.reason().trim();
        String remark = reason;
        if (StringUtils.hasText(req.sourceRefNo())) {
            remark += "；来源单号：" + req.sourceRefNo().trim();
        }
        Batch batch = inventory.getBatch();
        inventoryLogService.record(
                inventory.getProduct().getId(), inventory.getProduct().getSku(), inventory.getProduct().getName(),
                inventory.getLocation().getId(), inventory.getLocation().getCode(),
                "SCRAP", req.qty(), beforeQty, afterQty,
                "SCRAP", refNo, operator, remark,
                batch != null ? batch.getItemKey() : null,
                batch != null ? batch.getProductionDate() : null,
                batch != null ? batch.getShelfLifeDays() : null,
                batch != null ? batch.getManufacturer() : null
        );
        auditService.record("INVENTORY", inventory.getId(), refNo, "SCRAP", null, null,
                operator, "报废备件 " + inventory.getProduct().getSku() + "，数量 " + req.qty() +
                        "，库位 " + inventory.getLocation().getCode() + "；原因：" + reason);
    }

    /** 同一批次在两个库位间原子转移，并记录成对流水。 */
    @Transactional(rollbackFor = Exception.class)
    public InventoryTransferVO transfer(InventoryTransferReq req) {
        Inventory source = inventoryRepo.findWithGraphForUpdate(req.sourceInventoryId())
                .orElseThrow(() -> new BizException("来源库存记录不存在"));
        Location targetLocation = locationRepo.findById(req.targetLocationId())
                .orElseThrow(() -> new BizException("目标库位不存在"));
        if (Objects.equals(source.getLocation().getId(), targetLocation.getId())) {
            throw new BizException("目标库位不能与来源库位相同");
        }
        if (req.qty() > source.getAvailableQty()) {
            throw new BizException("移库数量不能超过来源可用库存 " + source.getAvailableQty());
        }
        String reason = req.reason().trim();
        if (reason.length() > 180) throw new BizException("移库原因不能超过180个字符");

        Batch batch = source.getBatch();
        Optional<Inventory> existingTarget = batch == null
                ? inventoryRepo.findByProductIdAndLocationIdAndBatchIsNullForUpdate(source.getProduct().getId(), targetLocation.getId())
                : inventoryRepo.findByProductIdAndLocationIdAndBatchIdForUpdate(source.getProduct().getId(), targetLocation.getId(), batch.getId());
        Inventory target = existingTarget.orElseGet(() ->
                new Inventory(source.getProduct(), targetLocation, batch, 0, java.time.Instant.now()));
        int sourceBefore = source.getQty();
        int targetBefore = target.getQty();
        int targetAfter;
        try {
            targetAfter = Math.addExact(targetBefore, req.qty());
        } catch (ArithmeticException ex) {
            throw new BizException("目标库存数量超出系统允许范围");
        }
        source.setQty(sourceBefore - req.qty());
        target.setQty(targetAfter);
        inventoryRepo.save(source);
        target = inventoryRepo.save(target);

        String transferNo = "TF" + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        String operator = currentOperator();
        String batchKey = batch != null ? batch.getItemKey() : null;
        java.time.LocalDate productionDate = batch != null ? batch.getProductionDate() : null;
        Integer shelfLifeDays = batch != null ? batch.getShelfLifeDays() : null;
        String manufacturer = batch != null ? batch.getManufacturer() : null;
        inventoryLogService.record(source.getProduct().getId(), source.getProduct().getSku(), source.getProduct().getName(),
                source.getLocation().getId(), source.getLocation().getCode(), "TRANSFER_OUT", req.qty(),
                sourceBefore, source.getQty(), "TRANSFER", transferNo, operator,
                "移至 " + targetLocation.getCode() + "；原因：" + reason,
                batchKey, productionDate, shelfLifeDays, manufacturer);
        inventoryLogService.record(source.getProduct().getId(), source.getProduct().getSku(), source.getProduct().getName(),
                targetLocation.getId(), targetLocation.getCode(), "TRANSFER_IN", req.qty(),
                targetBefore, targetAfter, "TRANSFER", transferNo, operator,
                "来自 " + source.getLocation().getCode() + "；原因：" + reason,
                batchKey, productionDate, shelfLifeDays, manufacturer);
        auditService.record("INVENTORY", source.getId(), transferNo, "TRANSFER", null, null,
                "备件 " + source.getProduct().getSku() + " 从 " + source.getLocation().getCode() +
                        " 移至 " + targetLocation.getCode() + "，数量 " + req.qty() + "；原因：" + reason);
        return new InventoryTransferVO(transferNo, source.getId(), target.getId(),
                source.getLocation().getCode(), targetLocation.getCode(), req.qty());
    }

    private String currentOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                ? String.valueOf(authentication.getPrincipal()) : "system";
    }
}
