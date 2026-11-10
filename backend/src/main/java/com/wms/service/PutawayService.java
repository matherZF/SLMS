package com.wms.service;

import com.wms.common.BizException;
import com.wms.dto.PutawayConfirmReq;
import com.wms.entity.*;
import com.wms.repository.InventoryRepository;
import com.wms.repository.LocationRepository;
import com.wms.repository.PutawayItemRepository;
import com.wms.repository.PutawayOrderRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class PutawayService {
    private final PutawayOrderRepository orderRepo;
    private final PutawayItemRepository itemRepo;
    private final LocationRepository locationRepo;
    private final InventoryRepository inventoryRepo;
    private final InventoryLogService inventoryLogService;
    private final OperationAuditService auditService;

    public PutawayService(PutawayOrderRepository orderRepo,
                          PutawayItemRepository itemRepo,
                          LocationRepository locationRepo,
                          InventoryRepository inventoryRepo,
                          InventoryLogService inventoryLogService, OperationAuditService auditService) {
        this.orderRepo = orderRepo;
        this.itemRepo = itemRepo;
        this.locationRepo = locationRepo;
        this.inventoryRepo = inventoryRepo;
        this.inventoryLogService = inventoryLogService;
        this.auditService = auditService;
    }

    /**
     * 确认一次上架。
     * <p>
     * 三个并发相关的改动：
     * <ol>
     *   <li><b>上架单加行锁</b>：原来直接 findById 读，两个并发请求会读到同一个 putQty，
     *       一个 10 件的计划能收进 15 件，或者后写的把先写的覆盖掉；</li>
     *   <li><b>库存记录加行锁</b>：库存行同样要 update ... where id，不锁就是丢失更新；</li>
     *   <li><b>幂等键</b>：同一个 requestId 重复提交直接返回上次结果，不再入库。</li>
     * </ol>
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirm(PutawayConfirmReq req) {
        Integer qty = req.qty();
        if (qty == null || qty <= 0) throw new BizException("数量必须大于0");
        if (req.orderId() == null) throw new BizException("orderId不能为空");
        if (req.locationId() == null) throw new BizException("locationId不能为空");
        String requestId = normalizeRequestId(req.requestId());

        // 幂等重放：先于加锁判断，重放请求不必去抢行锁
        if (requestId != null) {
            PutawayItem replayed = itemRepo.findByRequestId(requestId).orElse(null);
            if (replayed != null) {
                if (!Objects.equals(replayed.getPutawayOrder().getId(), req.orderId())) {
                    throw new BizException("该提交标识已被其它上架单使用，请刷新页面后重试");
                }
                PutawayOrder existing = orderRepo.findById(req.orderId()).orElseThrow(() -> new BizException("上架单不存在"));
                Map<String, Object> replay = buildResult(existing, replayed.getQty());
                replay.put("idempotent", true);
                replay.put("msg", "该次上架已提交成功，无需重复提交");
                return replay;
            }
        }

        PutawayOrder order = orderRepo.findDetailForUpdate(req.orderId())
                .orElseThrow(() -> new BizException("上架单不存在"));
        PutawayStatus previousStatus = order.getStatus();
        if (order.getStatus() == PutawayStatus.DONE) {
            throw new BizException("该上架单已完成，无需继续上架");
        }
        if (order.getPutQty() + qty > order.getPlanQty()) {
            int remain = order.getPlanQty() - order.getPutQty();
            throw new BizException(String.format("超过计划数量，剩余%d件", remain));
        }
        Location loc = locationRepo.findById(req.locationId())
                .orElseThrow(() -> new BizException("库位不存在"));

        PutawayItem item = new PutawayItem();
        item.setPutawayOrder(order);
        item.setLocation(loc);
        item.setQty(qty);
        item.setRequestId(requestId);
        item.setCreatedAt(Instant.now());
        order.getItems().add(item);

        order.setPutQty(order.getPutQty() + qty);
        if (order.getPutQty() == order.getPlanQty()) {
            order.setStatus(PutawayStatus.DONE);
        }

        orderRepo.save(order);

        Product product = order.getProduct();
        Batch batch = order.getBatch();
        Long batchId = batch != null ? batch.getId() : null;
        // 按 货品+库位+批次 定位库存记录（加锁：库存数量是 read-modify-write）
        Inventory inv;
        if (batchId != null) {
            inv = inventoryRepo.findByProductIdAndLocationIdAndBatchId(product.getId(), loc.getId(), batchId)
                    .flatMap(found -> inventoryRepo.findWithGraphForUpdate(found.getId()))
                    .orElse(null);
        } else {
            inv = inventoryRepo.findByProductIdAndLocationId(product.getId(), loc.getId())
                    .flatMap(found -> inventoryRepo.findWithGraphForUpdate(found.getId()))
                    .orElse(null);
        }
        int beforeQty = inv == null ? 0 : inv.getQty();
        int afterQty = beforeQty + qty;
        if (inv == null) {
            inv = new Inventory(product, loc, batch, qty, Instant.now());
        } else {
            inv.setQty(afterQty);
        }
        inventoryRepo.save(inv);

        // 记录入库日志（含批次信息）
        inventoryLogService.record(
                product.getId(), product.getSku(), product.getName(),
                loc.getId(), loc.getCode(),
                "INBOUND", qty, beforeQty, afterQty,
                "PUTAWAY", order.getOrderNo(),
                currentOperator(), null,
                batch != null ? batch.getItemKey() : null,
                batch != null ? batch.getProductionDate() : null,
                batch != null ? batch.getShelfLifeDays() : null,
                batch != null ? batch.getManufacturer() : null
        );
        auditService.record("PUTAWAY", order.getId(), order.getOrderNo(), "PUTAWAY_CONFIRM",
                previousStatus.name(), order.getStatus().name(),
                "备件 " + product.getSku() + " 上架至 " + loc.getCode() + "，数量 " + qty);

        Map<String, Object> result = buildResult(order, qty);
        result.put("idempotent", false);
        return result;
    }

    private Map<String, Object> buildResult(PutawayOrder order, int lastQty) {
        boolean done = order.getStatus() == PutawayStatus.DONE;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", order.getId());
        result.put("putQty", order.getPutQty());
        result.put("remaining", order.getPlanQty() - order.getPutQty());
        result.put("done", done);
        result.put("msg", done ? "上架完成，订单已结束" : "上架成功，数量 " + lastQty);
        return result;
    }

    private String normalizeRequestId(String value) {
        if (!StringUtils.hasText(value)) return null;
        String key = value.trim();
        if (key.length() > 64) throw new BizException("幂等键不能超过64个字符");
        return key;
    }

    private String currentOperator() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return String.valueOf(auth.getPrincipal());
    }
}
