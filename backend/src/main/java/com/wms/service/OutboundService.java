package com.wms.service;

import com.wms.common.BizException;
import com.wms.dto.*;
import com.wms.entity.*;
import com.wms.repository.EquipmentRepository;
import com.wms.repository.InventoryRepository;
import com.wms.repository.LocationRepository;
import com.wms.repository.OutboundItemRepository;
import com.wms.repository.OutboundOrderRepository;
import com.wms.repository.ProductRepository;
import com.wms.repository.EquipmentPartRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OutboundService {
    private final OutboundOrderRepository orderRepo;
    private final OutboundItemRepository itemRepo;
    private final LocationRepository locationRepo;
    private final ProductRepository productRepo;
    private final InventoryRepository inventoryRepo;
    private final InventoryLogService inventoryLogService;
    private final EquipmentPartRepository partRepo;
    private final EquipmentRepository equipmentRepo;
    private final SparePartUsageService sparePartUsageService;
    private final OperationAuditService auditService;
    private final ApprovalService approvalService;
    private final PendingBusinessActionService pendingActions;
    private final NumberSequenceService numberSequence;
    private final DeviceSignalPublisher deviceSignals;
    private final DeviceService deviceService;

    public OutboundService(OutboundOrderRepository orderRepo,
                           OutboundItemRepository itemRepo,
                           LocationRepository locationRepo,
                           ProductRepository productRepo,
                           InventoryRepository inventoryRepo,
                           InventoryLogService inventoryLogService, EquipmentPartRepository partRepo,
                           EquipmentRepository equipmentRepo,
                           SparePartUsageService sparePartUsageService, OperationAuditService auditService,
                           ApprovalService approvalService, PendingBusinessActionService pendingActions,
                           NumberSequenceService numberSequence, DeviceSignalPublisher deviceSignals,
                           DeviceService deviceService) {
        this.orderRepo = orderRepo;
        this.itemRepo = itemRepo;
        this.locationRepo = locationRepo;
        this.productRepo = productRepo;
        this.inventoryRepo = inventoryRepo;
        this.inventoryLogService = inventoryLogService;
        this.partRepo = partRepo;
        this.equipmentRepo = equipmentRepo;
        this.sparePartUsageService = sparePartUsageService;
        this.auditService = auditService;
        this.approvalService = approvalService;
        this.pendingActions = pendingActions;
        this.numberSequence = numberSequence;
        this.deviceSignals = deviceSignals;
        this.deviceService = deviceService;
    }

    /**
     * 生成领用单号：{@code OUT20261029-000001}。
     * <p>
     * 原来的实现是"查当天最大单号 + 1"，{@code synchronized} 只覆盖了查询，没覆盖 insert+commit，
     * 并发建单必撞唯一键；序号宽度 4 位在单日超 9999 单后还会永久撞号。
     * 现在交给号段表原子发号，见 {@link NumberSequenceService}。
     */
    public String generateOrderNo() {
        return numberSequence.nextOrderNo("OUT");
    }

    @Transactional
    public OutboundOrderDetailVO create(OutboundCreateReq req) {
        if (req.items() == null || req.items().isEmpty()) {
            throw new BizException("请至少选择一个货品");
        }
        OutboundOrder order = new OutboundOrder();
        order.setOrderNo(generateOrderNo());
        order.setStatus(OutboundStatus.PENDING_APPROVAL);
        order.setApplicant(currentOperator());
        order.setPurpose(req.purpose());
        order.setDepartment(trimToNull(req.department()));
        order.setRecipient(req.recipient().trim());
        order.setContactPhone(trimToNull(req.contactPhone()));
        order.setWorkOrderNo(trimToNull(req.workOrderNo()));
        order.setRequiredDate(req.requiredDate());
        order.setDeliveryLocation(trimToNull(req.deliveryLocation()));
        // 使用设备挂在明细上：一张单可能有多个备件、装在不同设备上，单头一个值会让所有明细记到同一台。
        // 单头那份只在「所有明细都指向同一台」时镜像过去，供列表页展示；不同就留空，避免看起来像整单都是这台。
        Map<Long, Equipment> chosenDevices = new LinkedHashMap<>();
        for (OutboundItemCreateReq ir : req.items()) {
            if (ir.requestedQty() == null || ir.requestedQty() <= 0) {
                throw new BizException("数量必须大于0");
            }
            Product product = productRepo.findById(ir.productId())
                    .orElseThrow(() -> new BizException("货品不存在: id=" + ir.productId()));
            OutboundItem item = new OutboundItem();
            item.setOutboundOrder(order);
            item.setProduct(product);
            if (ir.equipmentId() != null) {
                Equipment chosen = equipmentRepo.findById(ir.equipmentId())
                        .orElseThrow(() -> new BizException("所选设备不存在: id=" + ir.equipmentId()));
                item.setEquipment(chosen);
                item.setEquipmentCodeSnapshot(chosen.getCode());
                item.setEquipmentNameSnapshot(chosen.getName());
                chosenDevices.putIfAbsent(chosen.getId(), chosen);
            }
            item.setProductSkuSnapshot(product.getSku());
            item.setProductNameSnapshot(product.getName());
            item.setProductSpecSnapshot(product.getSpec());
            item.setProductUnitSnapshot(product.getUnit());
            item.setRequestedQty(ir.requestedQty());
            item.setPickedQty(0);
            // 明细没指定设备时，只有这个备件在基础数据里唯一绑定一台才自动填上；
            // 挂了多台或一台没挂就留空——通用件装到哪台只有领用人知道，系统不猜。
            if (item.getEquipment() == null) {
                Map<Long, Equipment> bound = new LinkedHashMap<>();
                partRepo.findByProductId(product.getId()).stream()
                        .map(EquipmentPart::getEquipment)
                        .filter(Objects::nonNull)
                        .forEach(e -> bound.putIfAbsent(e.getId(), e));
                if (bound.size() == 1) {
                    Equipment only = bound.values().iterator().next();
                    item.setEquipment(only);
                    item.setEquipmentCodeSnapshot(only.getCode());
                    item.setEquipmentNameSnapshot(only.getName());
                }
            }
            if (item.getEquipment() != null) {
                chosenDevices.putIfAbsent(item.getEquipment().getId(), item.getEquipment());
            }
            order.getItems().add(item);
        }
        // 单头那份只为了列表页展示：所有明细都指向同一台才镜像；跨设备则留空，
        // 否则列表会显示成整单都是某一台设备，与实际不符。
        if (chosenDevices.size() == 1) {
            Equipment only = chosenDevices.values().iterator().next();
            order.setEquipment(only);
            order.setEquipmentCodeSnapshot(only.getCode());
            order.setEquipmentNameSnapshot(only.getName());
        }
        orderRepo.save(order);
        int totalQty=order.getItems().stream().mapToInt(OutboundItem::getRequestedQty).sum();
        java.math.BigDecimal totalAmount=order.getItems().stream().map(i->{var price=i.getProduct().getReferenceUnitPrice();return (price==null?java.math.BigDecimal.ZERO:price).multiply(java.math.BigDecimal.valueOf(i.getRequestedQty()));}).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);
        java.util.Set<String> categories=order.getItems().stream().map(i->i.getProduct().getCategory()).filter(org.springframework.util.StringUtils::hasText).collect(java.util.stream.Collectors.toSet());
        ApprovalRecord approval=approvalService.submitOutbound(order.getId(),order.getOrderNo(),currentOperator(),new ApprovalService.WorkflowContext(categories,totalQty,totalAmount));
        if(approval==null||approval.getStatus()==ApprovalStatus.APPROVED){order.setStatus(OutboundStatus.APPROVED);order.setReviewedBy(approval==null?"system":currentOperator());order.setReviewOpinion(approval==null?"未命中审批条件，系统自动放行":"审批节点与申请人身份相同，系统自动通过");order.setReviewedAt(Instant.now());orderRepo.save(order);}
        auditService.record("OUTBOUND", order.getId(), order.getOrderNo(), "CREATE", null,
                order.getStatus().name(), currentOperator(),
                "创建领用申请；领用人：" + order.getRecipient() + "；部门：" + order.getDepartment()+"；总数量："+totalQty+"；参考金额："+totalAmount);
        return toDetail(order);
    }

    @Transactional(readOnly = true)
    public Page<OutboundOrderListItemVO> page(String status, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        OutboundStatus s = null;
        if (StringUtils.hasText(status)) {
            try { s = OutboundStatus.valueOf(status.trim().toUpperCase()); }
            catch (Exception e) { throw new BizException("状态参数无效"); }
        }
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return orderRepo.search(s, kw, pageable).map(this::toListItem);
    }

    public List<OutboundOrderListItemVO> pendingList() {
        return orderRepo.findPendingList(java.util.List.of(OutboundStatus.APPROVED, OutboundStatus.PICKING)).stream()
                .map(this::toListItem)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OutboundOrderDetailVO detail(Long id) {
        return orderRepo.findDetailById(id).map(this::toDetail)
                .orElseThrow(() -> new BizException("领用单不存在"));
    }

    /**
     * 查询某货品的可用库存列表（含批次详情），按 FIFO（到期日/生产日期升序）排序。
     * 用于出库时人工选择库位/批次。
     */
    @Transactional(readOnly = true)
    public List<AvailableStockVO> listAvailableInventory(Long productId) {
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new BizException("货品不存在"));
        List<Inventory> stocks = inventoryRepo.findAllByProductId(productId).stream()
                .filter(s -> s.getAvailableQty() > 0)
                .sorted(Comparator
                        .comparing((Inventory s) -> s.getBatch() != null ? s.getBatch().getExpiryDate() : null,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(s -> s.getBatch() != null ? s.getBatch().getProductionDate() : null,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Inventory::getQty, Comparator.reverseOrder()))
                .collect(Collectors.toList());
        return stocks.stream().map(s -> {
            Batch b = s.getBatch();
            return new AvailableStockVO(
                    s.getId(),
                    product.getId(), product.getSku(), product.getName(),
                    s.getLocation().getId(), s.getLocation().getCode(),
                    s.getLocation().getArea(), s.getLocation().getDeviceNo(),
                    s.getQty(), s.getReservedQty(), s.getAvailableQty(),
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
     * 人工为出库项分配库位+批次。
     */
    @Transactional
    public OutboundItemVO assignLocation(Long orderId, Long itemId, Long locationId, Long batchId) {
        OutboundOrder order = orderRepo.findDetailForUpdate(orderId)
                .orElseThrow(() -> new BizException("领用单不存在"));
        if (order.getStatus() != OutboundStatus.APPROVED && order.getStatus() != OutboundStatus.PICKING) {
            throw new BizException("当前状态不可分配库位");
        }
        OutboundItem item = order.getItems().stream()
                .filter(it -> it.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new BizException("拣货项不存在"));
        if (item.getProduct() == null) {
            throw new BizException("货品信息缺失");
        }
        // 校验该库存记录存在且数量充足
        Inventory stock;
        if (batchId != null) {
            stock = inventoryRepo.findByProductIdAndLocationIdAndBatchId(
                    item.getProduct().getId(), locationId, batchId)
                    .orElseThrow(() -> new BizException("指定库位/批次的库存不存在"));
        } else {
            stock = inventoryRepo.findByProductIdAndLocationId(item.getProduct().getId(), locationId)
                    .orElseThrow(() -> new BizException("指定库位的库存不存在"));
        }
        if (order.getStatus() == OutboundStatus.PICKING && item.getReservedQty() > 0) releaseReservation(item);
        stock = inventoryRepo.findWithGraphForUpdate(stock.getId())
                .orElseThrow(() -> new BizException("指定库存不存在"));
        if (stock.getAvailableQty() < item.getRequestedQty()) {
            throw new BizException(String.format("所选可用库存不足，仅剩 %d 件", stock.getAvailableQty()));
        }
        item.setLocation(stock.getLocation());
        item.setBatch(stock.getBatch());
        if (order.getStatus() == OutboundStatus.PICKING) reserve(item, stock);
        orderRepo.save(order);
        auditService.record("OUTBOUND", order.getId(), order.getOrderNo(), "ASSIGN_LOCATION",
                order.getStatus().name(), order.getStatus().name(), currentOperator(),
                "备件 " + item.getProduct().getSku() + " 分配至库位 " + stock.getLocation().getCode());

        // 点亮库位灯光 —— 仅在"开始拣货"时统一触发，分配库位阶段不主动点灯
        int avail = stock.getAvailableQty();
        Batch b = stock.getBatch();
        return new OutboundItemVO(
                item.getId(), item.getProduct().getId(), itemSku(item), itemName(item),
                itemSpec(item), itemUnit(item), itemEquipmentId(item), itemEquipmentCode(item), itemEquipmentName(item),
                item.getRequestedQty(), item.getPickedQty(),
                item.getReturnedQty(), Math.max(0, item.getPickedQty() - item.getReturnedQty()),
                stock.getLocation().getId(), stock.getLocation().getCode(),
                stock.getLocation().getArea(), stock.getLocation().getDeviceNo(), avail,
                b != null ? b.getId() : null,
                b != null ? b.getItemKey() : null,
                b != null ? b.getProductionDate() : null,
                b != null ? b.getShelfLifeDays() : null,
                b != null ? b.getManufacturer() : null,
                b != null ? b.getExpiryDate() : null,
                item.isScanVerified(), item.isButtonPressed(), item.getButtonPressedAt(),
                item.getLightConfirmed(), item.getLightConfirmedAt(), item.getLightMessage()
        );
    }

    /**
     * 开始拣货：为每个货品项按 FIFO 自动分配库位+批次，并点亮对应库位灯光设备。
     */
    @Transactional
    public Map<String, Object> startPicking(Long orderId) {
        OutboundOrder order = orderRepo.findDetailForUpdate(orderId)
                .orElseThrow(() -> new BizException("领用单不存在"));
        if (order.getStatus() != OutboundStatus.APPROVED) {
            throw new BizException("仅审批通过的领用单可开始拣货");
        }
        List<Map<String, Object>> pickingItems = new ArrayList<>();
        for (OutboundItem item : order.getItems()) {
            // 若已人工分配库位+批次，则保留，仅点亮灯光
            if (item.getLocation() != null) {
                Inventory assigned = findLockedInventory(item);
                reserve(item, assigned);
                // 生成 cmdtoken，用于亮灯结果回调的请求-响应匹配；
                // 真正的 HTTP 点灯放到事务提交之后执行（见 DeviceSignalListener）
                String lightCmdtoken = UUID.randomUUID().toString().replace("-", "");
                item.setLightCmdtoken(lightCmdtoken);
                item.setLightedAt(Instant.now());
                String deviceNo = item.getLocation().getDeviceNo();
                deviceSignals.light(item.getLocation().getId());
                Batch b = item.getBatch();
                Map<String, Object> mi = new LinkedHashMap<>();
                mi.put("itemId", item.getId());
                mi.put("productId", item.getProduct().getId());
                mi.put("sku", itemSku(item));
                mi.put("name", itemName(item));
                mi.put("spec", itemSpec(item));
                mi.put("unit", itemUnit(item));
                mi.put("requestedQty", item.getRequestedQty());
                mi.put("locationId", item.getLocation().getId());
                mi.put("locationCode", item.getLocation().getCode());
                mi.put("locationArea", item.getLocation().getArea());
                mi.put("deviceNo", deviceNo);
                int avail = 0;
                if (b != null) {
                    avail = assigned.getAvailableQty();
                } else {
                    avail = assigned.getAvailableQty();
                }
                mi.put("availableQty", avail);
                mi.put("batchId", b != null ? b.getId() : null);
                mi.put("itemKey", b != null ? b.getItemKey() : null);
                mi.put("productionDate", b != null ? b.getProductionDate() : null);
                mi.put("shelfLifeDays", b != null ? b.getShelfLifeDays() : null);
                mi.put("manufacturer", b != null ? b.getManufacturer() : null);
                mi.put("expiryDate", b != null ? b.getExpiryDate() : null);
                mi.put("scanVerified", item.isScanVerified());
                mi.put("buttonPressed", item.isButtonPressed());
                mi.put("buttonPressedAt", item.getButtonPressedAt());
                pickingItems.add(mi);
                continue;
            }
            // 找到该货品有库存的库位，按 FIFO 排序（到期日/生产日期升序），取第一个满足数量的
            List<Inventory> stocks = inventoryRepo.findAllByProductId(item.getProduct().getId()).stream()
                    .filter(s -> s.getAvailableQty() > 0)
                    .sorted(Comparator
                            .comparing((Inventory s) -> s.getBatch() != null ? s.getBatch().getExpiryDate() : null,
                                    Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(s -> s.getBatch() != null ? s.getBatch().getProductionDate() : null,
                                    Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(Inventory::getQty, Comparator.reverseOrder()))
                    .collect(Collectors.toList());
            Inventory stock = null;
            for (Inventory candidate : stocks) {
                Inventory locked = inventoryRepo.findWithGraphForUpdate(candidate.getId()).orElse(null);
                if (locked != null && locked.getAvailableQty() >= item.getRequestedQty()) {
                    stock = locked;
                    break;
                }
            }
            if (stock == null) {
                // 没有单个批次满足，取库存最大的一个提示不足
                Inventory maxStock = stocks.stream().max(Comparator.comparingInt(Inventory::getAvailableQty)).orElse(null);
                throw new BizException(String.format("货品 %s 无可用库存（最大单批次 %d 件）",
                        item.getProduct().getSku(),
                        maxStock != null ? maxStock.getAvailableQty() : 0));
            }
            item.setLocation(stock.getLocation());
            item.setBatch(stock.getBatch());
            reserve(item, stock);
            // 生成 cmdtoken，用于亮灯结果回调的请求-响应匹配；
            // 真正的 HTTP 点灯放到事务提交之后执行（见 DeviceSignalListener）
            String lightCmdtoken = UUID.randomUUID().toString().replace("-", "");
            item.setLightCmdtoken(lightCmdtoken);
            item.setLightedAt(Instant.now());
            String deviceNo = stock.getLocation().getDeviceNo();
            deviceSignals.light(stock.getLocation().getId());
            Batch b = stock.getBatch();
            Map<String, Object> mi = new LinkedHashMap<>();
            mi.put("itemId", item.getId());
            mi.put("productId", item.getProduct().getId());
            mi.put("sku", itemSku(item));
            mi.put("name", itemName(item));
            mi.put("spec", itemSpec(item));
            mi.put("unit", itemUnit(item));
            mi.put("requestedQty", item.getRequestedQty());
            mi.put("locationId", stock.getLocation().getId());
            mi.put("locationCode", stock.getLocation().getCode());
            mi.put("locationArea", stock.getLocation().getArea());
            mi.put("deviceNo", deviceNo);
            mi.put("availableQty", stock.getAvailableQty());
            // 批次信息
            mi.put("batchId", b != null ? b.getId() : null);
            mi.put("itemKey", b != null ? b.getItemKey() : null);
            mi.put("productionDate", b != null ? b.getProductionDate() : null);
            mi.put("shelfLifeDays", b != null ? b.getShelfLifeDays() : null);
            mi.put("manufacturer", b != null ? b.getManufacturer() : null);
            mi.put("expiryDate", b != null ? b.getExpiryDate() : null);
            mi.put("scanVerified", item.isScanVerified());
            mi.put("buttonPressed", item.isButtonPressed());
            mi.put("buttonPressedAt", item.getButtonPressedAt());
            pickingItems.add(mi);
        }
        OutboundStatus previousStatus = order.getStatus();
        order.setStatus(OutboundStatus.PICKING);
        order.setPickedBy(currentOperator());
        order.setPickedAt(Instant.now());
        orderRepo.save(order);
        auditService.record("OUTBOUND", order.getId(), order.getOrderNo(), "START_PICKING",
                previousStatus.name(), order.getStatus().name(), currentOperator(), "开始拣货并分配库存");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", order.getId());
        result.put("orderNo", order.getOrderNo());
        result.put("items", pickingItems);
        return result;
    }
    public List<OutboundOrderListItemVO> approvalTasks(){String user=currentOperator();return orderRepo.findPendingList(java.util.List.of(OutboundStatus.PENDING_APPROVAL)).stream().filter(o->approvalService.canHandle("OUTBOUND",o.getId(),user)).map(this::toListItem).toList();}

    /**
     * 出库最终确认前的双码核验：备件二维码和实际货位二维码必须同时匹配。
     */
    @Transactional
    public Map<String, Object> verifyScan(Long orderId, Long itemId, String partCode, String locationCode) {
        OutboundOrder order = orderRepo.findDetailById(orderId)
                .orElseThrow(() -> new BizException("领用单不存在"));
        if (order.getStatus() != OutboundStatus.PICKING) {
            throw new BizException("仅拣货中状态可进行扫码核验");
        }
        OutboundItem item = order.getItems().stream()
                .filter(it -> it.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new BizException("拣货项不存在"));
        if (item.getLocation() == null) {
            throw new BizException("当前拣货项尚未分配货位");
        }

        String normalizedPartCode = normalizeQrValue(partCode, "PART:");
        String normalizedLocationCode = normalizeQrValue(locationCode, "LOC:");
        if (!itemSku(item).equalsIgnoreCase(normalizedPartCode)) {
            throw new BizException("备件二维码与当前拣货项不匹配");
        }
        if (!item.getLocation().getCode().equalsIgnoreCase(normalizedLocationCode)) {
            throw new BizException("货位二维码与分配货位不匹配");
        }

        item.setScanVerified(true);
        item.setScanVerifiedAt(Instant.now());
        orderRepo.save(order);
        auditService.record("OUTBOUND", order.getId(), order.getOrderNo(), "SCAN_VERIFY",
                order.getStatus().name(), order.getStatus().name(), currentOperator(),
                "完成备件 " + item.getProduct().getSku() + " 与库位 " + item.getLocation().getCode() + " 双码核验");
        return Map.of(
                "itemId", item.getId(),
                "verified", true,
                "partCode", itemSku(item),
                "locationCode", item.getLocation().getCode(),
                "verifiedAt", item.getScanVerifiedAt());
    }

    @Transactional
    public Map<String, Object> review(Long orderId, boolean approved, String opinion) {
        OutboundOrder order = orderRepo.findDetailById(orderId)
                .orElseThrow(() -> new BizException("领用单不存在"));
        if (order.getStatus() != OutboundStatus.PENDING_APPROVAL) {
            throw new BizException("仅待审批状态的领用申请可审核");
        }
        OutboundStatus previousStatus = order.getStatus();
        var result=approvalService.handle("OUTBOUND", orderId, currentOperator(), approved, opinion);
        order.setStatus(!approved ? OutboundStatus.REJECTED : (result.completed()?OutboundStatus.APPROVED:OutboundStatus.PENDING_APPROVAL));
        order.setReviewedBy(currentOperator());
        order.setReviewOpinion(opinion == null ? null : opinion.trim());
        order.setReviewedAt(Instant.now());
        orderRepo.save(order);
        auditService.record("OUTBOUND", order.getId(), order.getOrderNo(), approved ? "APPROVE" : "REJECT",
                previousStatus.name(), order.getStatus().name(), currentOperator(),
                StringUtils.hasText(opinion) ? opinion.trim() : (approved ? "审批通过" : "审批驳回"));
        Map<String,Object> response=new LinkedHashMap<>();response.put("orderId",orderId);response.put("status",order.getStatus().name());response.put("completed",result.completed());if(result.nextNode()!=null)response.put("nextNode",result.nextNode());return response;
    }

    @Transactional
    public Map<String, Object> resubmit(Long orderId) {
        OutboundOrder order = orderRepo.findDetailById(orderId).orElseThrow(() -> new BizException("领用单不存在"));
        if (order.getStatus() != OutboundStatus.REJECTED) throw new BizException("仅已驳回领用单可以重新提交");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean canManage = auth != null && auth.getAuthorities().stream().anyMatch(a -> "outbound:manage".equals(a.getAuthority()));
        if (!canManage && !Objects.equals(order.getApplicant(), currentOperator())) throw new BizException("仅原申请人或具有领用单代管权限的用户可以重新提交");
        OutboundStatus previous = order.getStatus();
        order.setStatus(OutboundStatus.PENDING_APPROVAL); order.setReviewedBy(null); order.setReviewOpinion(null); order.setReviewedAt(null);
        orderRepo.save(order);
        int totalQty=order.getItems().stream().mapToInt(OutboundItem::getRequestedQty).sum();java.math.BigDecimal totalAmount=order.getItems().stream().map(i->{var p=i.getProduct().getReferenceUnitPrice();return(p==null?java.math.BigDecimal.ZERO:p).multiply(java.math.BigDecimal.valueOf(i.getRequestedQty()));}).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);java.util.Set<String> categories=order.getItems().stream().map(i->i.getProduct().getCategory()).filter(StringUtils::hasText).collect(java.util.stream.Collectors.toSet());ApprovalRecord approval=approvalService.submitOutbound(order.getId(),order.getOrderNo(),currentOperator(),new ApprovalService.WorkflowContext(categories,totalQty,totalAmount));if(approval==null||approval.getStatus()==ApprovalStatus.APPROVED)order.setStatus(OutboundStatus.APPROVED);orderRepo.save(order);
        auditService.record("OUTBOUND", order.getId(), order.getOrderNo(), "RESUBMIT", previous.name(), order.getStatus().name(), currentOperator(), "驳回后重新提交审批");
        return Map.of("orderId", orderId, "status", order.getStatus().name());
    }

    @Transactional
    public Map<String, Object> cancel(Long orderId, String reason) {
        OutboundOrder order = orderRepo.findDetailForUpdate(orderId)
                .orElseThrow(() -> new BizException("领用单不存在"));
        if (order.getStatus() != OutboundStatus.PENDING_APPROVAL && order.getStatus() != OutboundStatus.APPROVED
                && order.getStatus() != OutboundStatus.PICKING) {
            throw new BizException("仅待审批、待拣货或拣货中的领用单可以取消");
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean canManage = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "outbound:manage".equals(a.getAuthority()));
        if (!canManage && !Objects.equals(order.getApplicant(), currentOperator())) {
            throw new BizException("仅申请人或具有领用单代管权限的用户可以取消领用单");
        }
        OutboundStatus previousStatus = order.getStatus();
        if (previousStatus == OutboundStatus.PICKING) {
            for (OutboundItem item : order.getItems()) releaseReservation(item);
        }
        order.setStatus(OutboundStatus.CANCELLED);
        orderRepo.save(order);
        approvalService.cancel("OUTBOUND", orderId, currentOperator(), reason);
        auditService.record("OUTBOUND", order.getId(), order.getOrderNo(), "CANCEL",
                previousStatus.name(), order.getStatus().name(), currentOperator(),
                StringUtils.hasText(reason) ? reason.trim() : "取消领用申请");
        return Map.of("orderId", orderId, "status", order.getStatus().name());
    }

    /**
     * 拣货完成：扣减库存，更新已拣数量，若全部完成则单据置为 DONE。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> completePicking(Long orderId, String idempotencyKey) {
        OutboundOrder order = orderRepo.findDetailForUpdate(orderId)
                .orElseThrow(() -> new BizException("领用单不存在"));
        if (order.getStatus() == OutboundStatus.DONE) {
            // 幂等重放：携带相同幂等键的重复请求返回幂等结果；
            // 未带键或键不一致的重复提交视为新的请求，直接拒绝，避免掩盖重复扣库风险
            boolean replay = StringUtils.hasText(idempotencyKey)
                    && normalizeIdempotencyKey(idempotencyKey).equals(order.getCompletionKey());
            if (replay) {
                return Map.of("orderId", order.getId(), "done", true, "idempotent", true,
                        "msg", "该领用单已完成出库，无需重复扣减");
            }
            throw new BizException("该领用单已完成出库，请勿重复提交");
        }
        if (order.getStatus() != OutboundStatus.PICKING) {
            throw new BizException("仅拣货中状态可完成拣货");
        }
        // 归属校验：接口层已用 outbound:picking 收口，这里再挡一层横向越权，
        // 避免任何持有拣货权限的人都能扣减不属于自己的领用单库存。
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean canSupervise = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "outbound:manage".equals(a.getAuthority()));
        if (!canSupervise
                && !Objects.equals(order.getApplicant(), currentOperator())
                && !Objects.equals(order.getPickedBy(), currentOperator())) {
            throw new BizException("仅领用单申请人、拣货人或具有领用单代管权限的用户可以完成出库");
        }
        Instant completedAt = Instant.now();
        for (OutboundItem item : order.getItems()) {
            if (item.getLocation() == null) {
                throw new BizException("存在未分配库位的拣货项，请先开始拣货");
            }
            // 扫码核验由"必要条件"改为"可选条件"，按键回调作为必要条件；
            // 但电子标签开关未开启（eslabel.enabled=false）或库位未绑定标签（deviceNo 为空）时，
            // 标签根本没有参与本次出库（点灯已跳过），按键回调不可能到来，此时不再校验按键
            if (buttonRequired(item) && !item.isButtonPressed()) {
                throw new BizException(String.format(
                        "备件 %s 库位 %s 尚未收到电子标签按键回调，请在标签上按下右下按键后重试",
                        item.getProduct().getSku(),
                        item.getLocation().getCode()));
            }
            Long productId = item.getProduct().getId();
            Long locationId = item.getLocation().getId();
            Long batchId = item.getBatch() != null ? item.getBatch().getId() : null;

            Inventory inv = findLockedInventory(item);
            if (item.getReservedQty() < item.getRequestedQty() || inv.getReservedQty() < item.getRequestedQty())
                throw new BizException("库存占用记录异常，请取消拣货后重新发起");
            if (inv.getQty() < item.getRequestedQty()) {
                throw new BizException(String.format(
                        "库位 %s 货品 %s 库存不足，剩余 %d 件",
                        item.getLocation().getCode(), item.getProduct().getSku(), inv.getQty()));
            }
            int beforeQty = inv.getQty();
            int afterQty = beforeQty - item.getRequestedQty();
            inv.setQty(afterQty);
            inv.setReservedQty(inv.getReservedQty() - item.getReservedQty());
            item.setReservedQty(0);
            inventoryRepo.save(inv);
            item.setPickedQty(item.getRequestedQty());
            // 熄灭灯光（提交后执行，不再占着库存行锁等墨屏服务器）
            deviceSignals.lightOff(item.getLocation().getId());

            // 记录出库日志（含批次信息）
            Batch b = item.getBatch();
            inventoryLogService.record(
                    item.getProduct().getId(), itemSku(item), itemName(item),
                    item.getLocation().getId(), item.getLocation().getCode(),
                    "OUTBOUND", item.getRequestedQty(), beforeQty, afterQty,
                    "OUTBOUND", order.getOrderNo(),
                    currentOperator(), null,
                    b != null ? b.getItemKey() : null,
                    b != null ? b.getProductionDate() : null,
                    b != null ? b.getShelfLifeDays() : null,
                    b != null ? b.getManufacturer() : null
            );
            // 确认出库即视为备件开始使用；设备数据读取失败不能阻断库存出库。
            sparePartUsageService.createFromOutbound(order, item, completedAt);
        }
        OutboundStatus previousStatus = order.getStatus();
        order.setStatus(OutboundStatus.DONE);
        order.setCompletedBy(currentOperator());
        order.setCompletedAt(completedAt);
        order.setCompletionKey(normalizeIdempotencyKey(idempotencyKey));
        orderRepo.save(order);
        auditService.record("OUTBOUND", order.getId(), order.getOrderNo(), "COMPLETE",
                previousStatus.name(), order.getStatus().name(), currentOperator(), "完成扫码确认并扣减库存");
        return Map.of("orderId", order.getId(), "done", true, "msg", "拣货完成，出库成功");
    }

    /**
     * 按键事件回调处理（enterKey）。
     * <p>
     * 墨水屏服务器 POST 过来的按键报文：{ "mac": "...", "result": 0 }。
     * 找到该 MAC 绑定的 Location.deviceNo，再找到该库位上"最近一次点灯"的那<b>一条</b>
     * PICKING 出库项，把它置为已按键。completePicking() 会以此作为必要条件校验。
     * <p>
     * 这里刻意只确认一条：原实现把该库位上所有 PICKING 明细一次性置为已按键，
     * 只要有人在一张单上按了键，同一库位其他单的明细也一起被确认；
     * 而扫码早已降级为可选条件，等于不扫、不搬、不去现场也能出库。
     *
     * @param mac        触发按键的标签 MAC
     * @param buttonType 按键类型：0=右下按键；其他值=读卡器（读卡器 ID 后两位 int）
     * @return 被标记的出库项数量（0 或 1）；0 表示未找到可确认的 PICKING 出库项
     */
    @Transactional
    public int markButtonPressed(String mac, Integer buttonType) {
        if (mac == null || mac.isBlank()) return 0;
        Location loc = locationRepo.findByDeviceNo(mac);
        if (loc == null) return 0;
        // 已按 lighted_at desc 排序：刚亮过灯的那条排第一
        List<OutboundItem> items = itemRepo.findPickingByLocationOrderByLightedDesc(loc.getId(), OutboundStatus.PICKING);
        OutboundItem target = items.stream().filter(i -> !i.isButtonPressed()).findFirst().orElse(null);
        if (target == null) return 0;
        target.setButtonPressed(true);
        target.setButtonPressedAt(Instant.now());
        itemRepo.save(target);
        auditService.record("OUTBOUND", target.getOutboundOrder().getId(), target.getOutboundOrder().getOrderNo(),
                "BUTTON_PRESSED", target.getOutboundOrder().getStatus().name(),
                target.getOutboundOrder().getStatus().name(), "esl-callback",
                "库位 " + loc.getCode() + " 标签 " + mac + " 按键确认（按键类型 " + buttonType + "），"
                        + "备件：" + itemSku(target) + "，单据：" + target.getOutboundOrder().getOrderNo());
        return 1;
    }

    /** 退库严格关联已完成领用明细，并退回原库位、原批次。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> returnItem(Long orderId, OutboundReturnReq req) {
        OutboundOrder order = orderRepo.findDetailForUpdate(orderId)
                .orElseThrow(() -> new BizException("领用单不存在"));
        if (order.getStatus() != OutboundStatus.DONE) {
            throw new BizException("仅已完成的领用单可以退库");
        }
        OutboundItem item = order.getItems().stream()
                .filter(candidate -> Objects.equals(candidate.getId(), req.itemId()))
                .findFirst()
                .orElseThrow(() -> new BizException("领用明细不属于当前领用单"));
        int returnableQty = item.getPickedQty() - item.getReturnedQty();
        if (req.qty() > returnableQty) {
            throw new BizException("退库数量不能超过可退数量 " + returnableQty);
        }
        if (item.getLocation() == null) {
            throw new BizException("原领用明细缺少出库库位，无法退库");
        }

        ReturnActionPayload payload = new ReturnActionPayload(orderId, item.getId(), req.qty(), req.reason().trim());
        var submission = pendingActions.submit("RETURN", item.getId(), "RT", currentOperator(), payload);
        PendingBusinessAction action = submission.action();
        if (submission.completed()) { executeApprovedReturn(payload, currentOperator()); pendingActions.finish(action, "APPROVED"); }
        auditService.record("OUTBOUND", order.getId(), order.getOrderNo(), "RETURN_SUBMIT",
                order.getStatus().name(), order.getStatus().name(), currentOperator(),
                "提交退库审批：" + action.getBusinessNo() + "；备件：" + itemSku(item) + "；数量：" + req.qty());
        return Map.of("returnNo", action.getBusinessNo(), "actionId", action.getId(),
                "status", submission.completed() ? "APPROVED" : "PENDING_APPROVAL", "outboundOrderNo", order.getOrderNo());
    }

    /** 最终审批通过后执行退库；重新锁定并校验领用明细，防止审批期间数量失效。 */
    @Transactional(rollbackFor = Exception.class)
    public void executeApprovedReturn(ReturnActionPayload req, String operator) {
        OutboundOrder order = orderRepo.findDetailForUpdate(req.orderId())
                .orElseThrow(() -> new BizException("领用单不存在"));
        if (order.getStatus() != OutboundStatus.DONE) throw new BizException("仅已完成的领用单可以退库");
        OutboundItem item = order.getItems().stream().filter(i -> Objects.equals(i.getId(), req.itemId()))
                .findFirst().orElseThrow(() -> new BizException("领用明细不属于当前领用单"));
        int returnableQty = item.getPickedQty() - item.getReturnedQty();
        if (req.qty() > returnableQty) throw new BizException("审批执行失败：当前可退数量仅剩 " + returnableQty);
        if (item.getLocation() == null) throw new BizException("原领用明细缺少出库库位，无法退库");

        Long productId = item.getProduct().getId();
        Long locationId = item.getLocation().getId();
        Batch batch = item.getBatch();
        Inventory inventory = batch == null
                ? inventoryRepo.findByProductIdAndLocationId(productId, locationId)
                    .orElseThrow(() -> new BizException("原库存记录不存在"))
                : inventoryRepo.findByProductIdAndLocationIdAndBatchId(productId, locationId, batch.getId())
                    .orElseThrow(() -> new BizException("原库存批次记录不存在"));
        int beforeQty = inventory.getQty();
        int afterQty;
        try {
            afterQty = Math.addExact(beforeQty, req.qty());
        } catch (ArithmeticException ex) {
            throw new BizException("退库后库存数量超出系统允许范围");
        }
        inventory.setQty(afterQty);
        item.setReturnedQty(item.getReturnedQty() + req.qty());
        inventoryRepo.save(inventory);
        orderRepo.save(order);

        String returnNo = "RT" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        sparePartUsageService.applyReturn(item.getId(), req.qty(), returnNo, req.reason());
        inventoryLogService.record(
                productId, itemSku(item), itemName(item),
                locationId, item.getLocation().getCode(), "RETURN", req.qty(), beforeQty, afterQty,
                "OUTBOUND_RETURN", order.getOrderNo(), operator,
                "退库单号：" + returnNo + "；原因：" + req.reason(),
                batch != null ? batch.getItemKey() : null,
                batch != null ? batch.getProductionDate() : null,
                batch != null ? batch.getShelfLifeDays() : null,
                batch != null ? batch.getManufacturer() : null
        );
        auditService.record("OUTBOUND", order.getId(), order.getOrderNo(), "RETURN",
                order.getStatus().name(), order.getStatus().name(), operator,
                "退库单号：" + returnNo + "；备件：" + item.getProduct().getSku() +
                        "；数量：" + req.qty() + "；原因：" + req.reason());
    }

    private OutboundOrderListItemVO toListItem(OutboundOrder o) {
        return new OutboundOrderListItemVO(
                o.getId(), o.getOrderNo(), o.getStatus().name(),
                o.getItems().size(), o.getApplicant(), o.getDepartment(), o.getRecipient(),
                o.getPurpose(), o.getCreatedAt()
        );
    }

    private OutboundOrderDetailVO toDetail(OutboundOrder o) {
        List<OutboundItemVO> itemVOs = o.getItems().stream()
                .map(it -> {
                    Long locId = it.getLocation() != null ? it.getLocation().getId() : null;
                    String locCode = it.getLocation() != null ? it.getLocation().getCode() : null;
                    String locArea = it.getLocation() != null ? it.getLocation().getArea() : null;
                    String deviceNo = it.getLocation() != null ? it.getLocation().getDeviceNo() : null;
                    int avail = 0;
                    Batch b = it.getBatch();
                    Long batchId = b != null ? b.getId() : null;
                    if (locId != null && batchId != null) {
                        avail = inventoryRepo.findByProductIdAndLocationIdAndBatchId(it.getProduct().getId(), locId, batchId)
                                .map(Inventory::getAvailableQty).orElse(0);
                    } else if (locId != null) {
                        avail = inventoryRepo.findByProductIdAndLocationId(it.getProduct().getId(), locId)
                                .map(Inventory::getAvailableQty).orElse(0);
                    }
                    return new OutboundItemVO(
                            it.getId(), it.getProduct().getId(), itemSku(it), itemName(it),
                            itemSpec(it), itemUnit(it), itemEquipmentId(it), itemEquipmentCode(it), itemEquipmentName(it),
                            it.getRequestedQty(), it.getPickedQty(),
                            it.getReturnedQty(), Math.max(0, it.getPickedQty() - it.getReturnedQty()),
                            locId, locCode, locArea, deviceNo, avail,
                            batchId,
                            b != null ? b.getItemKey() : null,
                            b != null ? b.getProductionDate() : null,
                            b != null ? b.getShelfLifeDays() : null,
                            b != null ? b.getManufacturer() : null,
                            b != null ? b.getExpiryDate() : null,
                            it.isScanVerified(), it.isButtonPressed(), it.getButtonPressedAt(),
                            it.getLightConfirmed(), it.getLightConfirmedAt(), it.getLightMessage()
                    );
                }).collect(Collectors.toList());
        Equipment equipment = o.getEquipment();
        return new OutboundOrderDetailVO(o.getId(), o.getOrderNo(), o.getStatus().name(),
                itemVOs, o.getCreatedAt(), o.getApplicant(), o.getDepartment(), o.getRecipient(),
                o.getContactPhone(), o.getPurpose(), o.getWorkOrderNo(), o.getRequiredDate(),
                o.getDeliveryLocation(), equipment != null ? equipment.getId() : null,
                StringUtils.hasText(o.getEquipmentCodeSnapshot()) ? o.getEquipmentCodeSnapshot() : (equipment != null ? equipment.getCode() : null),
                StringUtils.hasText(o.getEquipmentNameSnapshot()) ? o.getEquipmentNameSnapshot() : (equipment != null ? equipment.getName() : null),
                o.getReviewedBy(), o.getReviewOpinion(), o.getReviewedAt(), o.getPickedBy(), o.getPickedAt(),
                o.getCompletedBy(), o.getCompletedAt(), approvalService.list("OUTBOUND", o.getId()), auditService.list("OUTBOUND", o.getId()),
                inventoryLogService.listByRefNo(o.getOrderNo()));
    }

    private String currentOperator() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return String.valueOf(auth.getPrincipal());
    }

    /**
     * 该拣货项是否必须校验电子标签按键回传。
     * <p>
     * 只有"标签真的参与了这个库位的本次出库"才校验：
     * <ul>
     *   <li>电子标签开关关闭（eslabel.enabled=false）——点灯本身就是桩实现，回调永远不来；</li>
     *   <li>库位未绑定标签（deviceNo 为空）——没有可按的标签。</li>
     * </ul>
     * 两种情况都与 {@link DeviceService#lightUp} 跳过点灯的条件保持一致，避免
     * "灯没亮却要求按灯上按键"的死锁。
     */
    private boolean buttonRequired(OutboundItem item) {
        Location loc = item.getLocation();
        if (loc == null) return false;
        String mac = loc.getDeviceNo();
        if (mac == null || mac.isBlank()) return false;
        return deviceService.isEslEnabled();
    }

    private Inventory findLockedInventory(OutboundItem item) {
        if (item.getLocation() == null) throw new BizException("拣货项尚未分配库位");
        Optional<Inventory> found = item.getBatch() == null
                ? inventoryRepo.findByProductIdAndLocationId(item.getProduct().getId(), item.getLocation().getId())
                : inventoryRepo.findByProductIdAndLocationIdAndBatchId(
                        item.getProduct().getId(), item.getLocation().getId(), item.getBatch().getId());
        Inventory snapshot = found.orElseThrow(() -> new BizException("库存记录不存在"));
        return inventoryRepo.findWithGraphForUpdate(snapshot.getId())
                .orElseThrow(() -> new BizException("库存记录不存在"));
    }

    private void reserve(OutboundItem item, Inventory inventory) {
        if (item.getReservedQty() > 0) return;
        if (inventory.getAvailableQty() < item.getRequestedQty())
            throw new BizException(String.format("库位 %s 可用库存不足，仅剩 %d 件",
                    inventory.getLocation().getCode(), inventory.getAvailableQty()));
        inventory.setReservedQty(inventory.getReservedQty() + item.getRequestedQty());
        item.setReservedQty(item.getRequestedQty());
        inventoryRepo.save(inventory);
    }

    private void releaseReservation(OutboundItem item) {
        if (item.getReservedQty() <= 0) return;
        Inventory inventory = findLockedInventory(item);
        if (inventory.getReservedQty() < item.getReservedQty())
            throw new BizException("库存占用记录异常，无法释放，请联系管理员");
        inventory.setReservedQty(inventory.getReservedQty() - item.getReservedQty());
        item.setReservedQty(0);
        inventoryRepo.save(inventory);
        if (item.getLocation() != null) deviceSignals.lightOff(item.getLocation().getId());
    }

    private String normalizeIdempotencyKey(String value) {
        if (!StringUtils.hasText(value)) return "OUTBOUND-" + UUID.randomUUID();
        String key = value.trim();
        if (key.length() > 64) throw new BizException("幂等键不能超过64个字符");
        return key;
    }

    private String normalizeQrValue(String value, String prefix) {
        if (value == null || value.isBlank()) {
            throw new BizException("二维码内容不能为空");
        }
        String normalized = value.trim();
        if (normalized.regionMatches(true, 0, prefix, 0, prefix.length())) {
            normalized = normalized.substring(prefix.length()).trim();
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String itemSku(OutboundItem item) {
        return StringUtils.hasText(item.getProductSkuSnapshot()) ? item.getProductSkuSnapshot() : item.getProduct().getSku();
    }

    private String itemName(OutboundItem item) {
        return StringUtils.hasText(item.getProductNameSnapshot()) ? item.getProductNameSnapshot() : item.getProduct().getName();
    }

    private String itemSpec(OutboundItem item) {
        return item.getProductSpecSnapshot() != null ? item.getProductSpecSnapshot() : item.getProduct().getSpec();
    }

    private String itemUnit(OutboundItem item) {
        return item.getProductUnitSnapshot() != null ? item.getProductUnitSnapshot() : item.getProduct().getUnit();
    }

    /** 明细上那台设备：优先用实时关联，没有再退回建档时的快照（设备改名也不丢） */
    private Long itemEquipmentId(OutboundItem item) {
        return item.getEquipment() != null ? item.getEquipment().getId() : null;
    }

    private String itemEquipmentCode(OutboundItem item) {
        if (item.getEquipment() != null) return item.getEquipment().getCode();
        return item.getEquipmentCodeSnapshot();
    }

    private String itemEquipmentName(OutboundItem item) {
        if (item.getEquipment() != null) return item.getEquipment().getName();
        return item.getEquipmentNameSnapshot();
    }
}
