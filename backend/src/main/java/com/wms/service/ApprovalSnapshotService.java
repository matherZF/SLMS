package com.wms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.common.BizException;
import com.wms.dto.InventoryAdjustmentReq;
import com.wms.dto.ReturnActionPayload;
import com.wms.entity.*;
import com.wms.repository.*;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 审批业务快照服务：根据审批任务的 businessType + businessId，组装审批人可据此判断的业务详情。
 * 快照直接返回 Map（简单 key-value + items 列表），前端按业务类型差异化渲染。
 */
@Service
public class ApprovalSnapshotService {
    private final OutboundOrderRepository outboundOrderRepo;
    private final OutboundItemRepository outboundItemRepo;
    private final PendingBusinessActionRepository actionRepo;
    private final InventoryRepository inventoryRepo;
    private final StocktakeOrderRepository stocktakeRepo;
    private final ObjectMapper json;

    public ApprovalSnapshotService(OutboundOrderRepository outboundOrderRepo,
                                   OutboundItemRepository outboundItemRepo,
                                   PendingBusinessActionRepository actionRepo,
                                   InventoryRepository inventoryRepo,
                                   StocktakeOrderRepository stocktakeRepo,
                                   ObjectMapper json) {
        this.outboundOrderRepo = outboundOrderRepo;
        this.outboundItemRepo = outboundItemRepo;
        this.actionRepo = actionRepo;
        this.inventoryRepo = inventoryRepo;
        this.stocktakeRepo = stocktakeRepo;
        this.json = json;
    }

    public Map<String, Object> build(String businessType, Long businessId) {
        if (businessType == null || businessId == null) return Map.of();
        return switch (businessType) {
            case "OUTBOUND" -> snapshotOutbound(businessId);
            case "RETURN" -> snapshotReturn(businessId);
            case "SCRAP" -> snapshotScrap(businessId);
            case "STOCKTAKE_DIFF" -> snapshotStocktakeDiff(businessId);
            default -> Map.of();
        };
    }

    /** 领用申请：businessId = OutboundOrder.id */
    private Map<String, Object> snapshotOutbound(Long orderId) {
        OutboundOrder o = outboundOrderRepo.findDetailById(orderId).orElse(null);
        if (o == null) return Map.of();
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("orderNo", o.getOrderNo());
        s.put("purpose", o.getPurpose());
        s.put("department", o.getDepartment());
        s.put("recipient", o.getRecipient());
        s.put("contactPhone", o.getContactPhone());
        s.put("workOrderNo", o.getWorkOrderNo());
        s.put("requiredDate", o.getRequiredDate());
        s.put("deliveryLocation", o.getDeliveryLocation());
        s.put("equipment", o.getEquipmentNameSnapshot());
        List<Map<String, Object>> items = new ArrayList<>();
        int totalQty = 0;
        for (OutboundItem it : o.getItems()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sku", it.getProductSkuSnapshot());
            m.put("name", it.getProductNameSnapshot());
            m.put("spec", it.getProductSpecSnapshot());
            m.put("unit", it.getProductUnitSnapshot());
            m.put("requestedQty", it.getRequestedQty());
            m.put("location", it.getLocation() != null ? it.getLocation().getCode() : null);
            m.put("equipment", it.getEquipmentNameSnapshot());
            items.add(m);
            totalQty += it.getRequestedQty();
        }
        s.put("items", items);
        s.put("totalQty", totalQty);
        return s;
    }

    /** 退库申请：businessId = PendingBusinessAction.id */
    private Map<String, Object> snapshotReturn(Long actionId) {
        PendingBusinessAction action = actionRepo.findById(actionId).orElse(null);
        if (action == null) return Map.of();
        ReturnActionPayload p = parsePayload(action.getPayload(), ReturnActionPayload.class);
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("reason", p.reason());
        s.put("returnQty", p.qty());
        // 关联的领用明细
        OutboundItem item = outboundItemRepo.findById(p.itemId()).orElse(null);
        if (item != null) {
            s.put("sku", item.getProductSkuSnapshot());
            s.put("name", item.getProductNameSnapshot());
            s.put("spec", item.getProductSpecSnapshot());
            s.put("unit", item.getProductUnitSnapshot());
            s.put("requestedQty", item.getRequestedQty());
            s.put("pickedQty", item.getPickedQty());
            s.put("returnedQty", item.getReturnedQty());
            s.put("location", item.getLocation() != null ? item.getLocation().getCode() : null);
            OutboundOrder order = item.getOutboundOrder();
            if (order != null) {
                s.put("orderNo", order.getOrderNo());
                s.put("applicant", order.getApplicant());
                s.put("department", order.getDepartment());
            }
        }
        return s;
    }

    /** 报废申请：businessId = PendingBusinessAction.id */
    private Map<String, Object> snapshotScrap(Long actionId) {
        PendingBusinessAction action = actionRepo.findById(actionId).orElse(null);
        if (action == null) return Map.of();
        InventoryAdjustmentReq p = parsePayload(action.getPayload(), InventoryAdjustmentReq.class);
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", p.type());
        s.put("reason", p.reason());
        s.put("sourceRefNo", p.sourceRefNo());
        s.put("scrapQty", p.qty());
        Inventory inv = inventoryRepo.findWithGraphById(p.inventoryId()).orElse(null);
        if (inv != null) {
            Product prod = inv.getProduct();
            s.put("sku", prod != null ? prod.getSku() : null);
            s.put("productName", prod != null ? prod.getName() : null);
            s.put("spec", prod != null ? prod.getSpec() : null);
            s.put("unit", prod != null ? prod.getUnit() : null);
            Location loc = inv.getLocation();
            s.put("location", loc != null ? loc.getCode() : null);
            Batch batch = inv.getBatch();
            s.put("batchNo", batch != null ? batch.getItemKey() : null);
            s.put("currentStock", inv.getQty());
        }
        return s;
    }

    /** 盘点差异复核：businessId = PendingBusinessAction.id */
    private Map<String, Object> snapshotStocktakeDiff(Long actionId) {
        PendingBusinessAction action = actionRepo.findById(actionId).orElse(null);
        if (action == null) return Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> payloadMap = parsePayload(action.getPayload(), Map.class);
        Object orderIdObj = payloadMap.get("orderId");
        if (orderIdObj == null) return Map.of();
        Long orderId = ((Number) orderIdObj).longValue();
        StocktakeOrder order = stocktakeRepo.findDetail(orderId).orElse(null);
        if (order == null) return Map.of();
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("orderNo", order.getOrderNo());
        s.put("remark", order.getRemark());
        s.put("createdBy", order.getCreatedBy());
        List<Map<String, Object>> diffs = new ArrayList<>();
        for (StocktakeItem si : order.getItems()) {
            Integer actual = si.getActualQty();
            if (actual == null || actual == si.getBookQty()) continue; // 只关心差异项
            Inventory inv = si.getInventory();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sku", inv != null && inv.getProduct() != null ? inv.getProduct().getSku() : null);
            m.put("productName", inv != null && inv.getProduct() != null ? inv.getProduct().getName() : null);
            m.put("spec", inv != null && inv.getProduct() != null ? inv.getProduct().getSpec() : null);
            m.put("location", inv != null && inv.getLocation() != null ? inv.getLocation().getCode() : null);
            m.put("batchNo", inv != null && inv.getBatch() != null ? inv.getBatch().getItemKey() : null);
            m.put("bookQty", si.getBookQty());
            m.put("actualQty", actual);
            m.put("diffQty", actual - si.getBookQty());
            diffs.add(m);
        }
        s.put("diffItems", diffs);
        s.put("diffCount", diffs.size());
        return s;
    }

    private <T> T parsePayload(String jsonStr, Class<T> type) {
        try {
            return json.readValue(jsonStr, type);
        } catch (Exception e) {
            throw new BizException("业务快照解析失败: " + e.getMessage());
        }
    }
}
