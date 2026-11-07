package com.wms.service;

import com.wms.dto.InventoryLogVO;
import com.wms.entity.*;
import com.wms.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {
    private final InventoryRepository inventoryRepo;
    private final InventoryLogRepository logRepo;
    private final OutboundOrderRepository outboundRepo;
    private final StocktakeOrderRepository stocktakeRepo;
    private final SparePartAlertRepository alertRepo;
    private final AlertService alertService;
    private final ApprovalService approvalService;

    public DashboardService(InventoryRepository inventoryRepo, InventoryLogRepository logRepo,
                            OutboundOrderRepository outboundRepo, StocktakeOrderRepository stocktakeRepo,
                            SparePartAlertRepository alertRepo, AlertService alertService,
                            ApprovalService approvalService) {
        this.inventoryRepo = inventoryRepo;
        this.logRepo = logRepo;
        this.outboundRepo = outboundRepo;
        this.stocktakeRepo = stocktakeRepo;
        this.alertRepo = alertRepo;
        this.alertService = alertService;
        this.approvalService = approvalService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        List<Inventory> inventories = inventoryRepo.findAllWithGraph();
        int totalQty = inventories.stream().mapToInt(Inventory::getQty).sum();
        long skuCount = inventories.stream().filter(i -> i.getQty() > 0)
                .map(i -> i.getProduct().getId()).distinct().count();
        long locationCount = inventories.stream().filter(i -> i.getQty() > 0)
                .map(i -> i.getLocation().getId()).distinct().count();
        long expiryCount = inventories.stream().filter(i -> i.getQty() > 0 && i.getBatch() != null
                && i.getBatch().getExpiryDate() != null
                && !i.getBatch().getExpiryDate().isAfter(LocalDate.now().plusDays(30))).count();
        // 预警数：按当前用户视角统计可见 OPEN（排除已忽略），与预警中心 inbox() 同口径
        long alertCount = alertService.visibleOpenCount();
        // 未读数按当前用户独立算（已读/忽略各用户独立）
        long alertUnread = alertService.unreadCount();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalQty", totalQty);
        result.put("skuCount", skuCount);
        result.put("locationCount", locationCount);
        result.put("expiryCount", expiryCount);
        result.put("alertCount", alertCount);
        result.put("alertUnread", alertUnread);
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        var approvalTasks=auth==null?java.util.List.<com.wms.dto.ApprovalTaskVO>of():approvalService.tasks(auth.getName());
        result.put("pendingApproval", approvalTasks.size());
        result.put("overdueApproval", approvalTasks.stream().filter(com.wms.dto.ApprovalTaskVO::overdue).count());
        result.put("pendingPicking", outboundRepo.countByStatus(OutboundStatus.APPROVED)
                + outboundRepo.countByStatus(OutboundStatus.PICKING));
        result.put("stocktakeCounting", stocktakeRepo.countByStatus(StocktakeStatus.COUNTING));
        result.put("recentLogs", logRepo.findTop10ByOrderByCreatedAtDesc().stream().map(this::logMap).toList());
        return result;
    }

    private Map<String, Object> logMap(InventoryLog log) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", log.getId()); value.put("createdAt", log.getCreatedAt());
        value.put("changeType", log.getChangeType()); value.put("sku", log.getSku());
        value.put("productName", log.getProductName()); value.put("locationCode", log.getLocationCode());
        value.put("changeQty", log.getChangeQty()); value.put("beforeQty", log.getBeforeQty());
        value.put("afterQty", log.getAfterQty()); value.put("refNo", log.getRefNo());
        value.put("operator", log.getOperator());
        return value;
    }
}
