package com.wms.service;

import com.wms.dto.AlertItem;
import com.wms.dto.LifeMetricUsageVO;
import com.wms.dto.SparePartForecastVO;
import com.wms.entity.*;
import com.wms.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 备件预警。
 *
 * <p>四类：{@code STOCK} 已经不够、{@code EXPIRY} 快过期、{@code LIFECYCLE} 装在设备上的该换了、
 * {@code FORECAST} 按消耗速度算出来什么时候会不够。前三类是「已经发生或马上发生」，
 * 第四类是「按趋势推算」，两者不会同时出现在同一个备件上（见 {@code covered}）。</p>
 *
 * <p><b>两个阈值各管一件事</b>：补货点决定「什么时候该下单」，安全库存决定「有多严重」。
 * 库存落到补货点与安全库存之间，按常规交期下单还来得及；落到安全库存以下说明已经在动用缓冲，
 * 常规交期多半来不及，必须升级成加急。</p>
 *
 * <p><b>用户维度状态</b>：已读、忽略各用户独立，互不影响；已处理(ACK)是全局状态，
 * 一旦有人处理，所有人看到的都是已处理。</p>
 */
@Service
public class AlertService {
    private final ProductRepository productRepo;
    private final InventoryRepository inventoryRepo;
    private final SparePartUsageRepository usageRepo;
    private final EquipmentPartRepository partRepo;
    private final SparePartAlertRepository alertRepo;
    private final AlertUserStateRepository alertUserStateRepo;
    private final UserRepository userRepo;
    private final SparePartForecastService forecastService;
    private final SystemConfigService configService;
    private final OperationAuditService auditService;

    /** 更换预警提前量（天）：只负责安排检修，提前买货由库存预测负责。可在系统配置里改 */
    private final int lifeWarningDays;
    /** 备件没填采购周期时按这个天数算补货点，与需求预测保持同一个默认值 */
    private final int defaultLeadDays;

    public AlertService(ProductRepository productRepo, InventoryRepository inventoryRepo,
                        SparePartUsageRepository usageRepo, EquipmentPartRepository partRepo,
                        SparePartAlertRepository alertRepo,
                        AlertUserStateRepository alertUserStateRepo,
                        UserRepository userRepo,
                        SparePartForecastService forecastService,
                        SystemConfigService configService,
                        OperationAuditService auditService,
                        @Value("${spare-part.life.warning-days:3}") int lifeWarningDays,
                        @Value("${spare-part.forecast.default-lead-days:7}") int defaultLeadDays) {
        this.productRepo = productRepo; this.inventoryRepo = inventoryRepo;
        this.usageRepo = usageRepo; this.partRepo = partRepo;
        this.alertRepo = alertRepo;
        this.alertUserStateRepo = alertUserStateRepo;
        this.userRepo = userRepo;
        this.forecastService = forecastService;
        this.configService = configService;
        this.auditService = auditService;
        this.lifeWarningDays = lifeWarningDays; this.defaultLeadDays = defaultLeadDays;
    }

    /** 寿命预警提前量：备件档案与台账都没单独设时用系统配置值（与台账状态判定同一个值） */
    private int lifeWarningDays() { return configService.lifeWarningDays() > 0 ? configService.lifeWarningDays() : lifeWarningDays; }
    /** 效期预警提前量 */
    private int expiryAheadDays() { return configService.expiryAheadDays() > 0 ? configService.expiryAheadDays() : 30; }
    private int defaultLeadDays() { return configService.defaultLeadDays() > 0 ? configService.defaultLeadDays() : defaultLeadDays; }

    /** 实时计算，给页面用 */
    public List<Map<String, Object>> alerts() {
        return compute().stream().map(AlertItem::toMap).toList();
    }

    /** 实时计算 */
    public List<AlertItem> compute() {
        LocalDate today = LocalDate.now();
        List<AlertItem> result = new ArrayList<>();
        Map<Long, Integer> stockByProduct = stockByProduct();
        Map<Long, List<EquipmentPart>> linksByProduct = partRepo.findAll().stream()
                .collect(Collectors.groupingBy(l -> l.getProduct().getId()));
        Map<Long, Product> productById = productRepo.findAll().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));
        // 预测只算一次：既用于库存预警的日耗与建议量，也单独成为一类预警
        List<SparePartForecastVO> forecasts = forecastService.forecast();
        Map<Long, SparePartForecastVO> forecastByProduct = forecasts.stream()
                .collect(Collectors.toMap(SparePartForecastVO::productId, Function.identity(), (a, b) -> a));

        result.addAll(expiryAlerts(today));
        Set<Long> covered = new HashSet<>();
        result.addAll(stockAlerts(today, stockByProduct, linksByProduct, forecastByProduct, covered));
        result.addAll(forecastAlerts(forecasts, covered));
        result.addAll(replaceAlerts(today, stockByProduct, productById));
        return result;
    }

    private Map<Long, Integer> stockByProduct() {
        Map<Long, Integer> stockByProduct = new HashMap<>();
        for (Inventory inventory : inventoryRepo.findAllWithGraph()) {
            stockByProduct.merge(inventory.getProduct().getId(), inventory.getAvailableQty(), Integer::sum);
        }
        return stockByProduct;
    }

    private List<AlertItem> expiryAlerts(LocalDate today) {
        List<AlertItem> rows = new ArrayList<>();
        for (Inventory inventory : inventoryRepo.findAllWithGraph()) {
            if (inventory.getQty() <= 0 || inventory.getBatch() == null
                    || inventory.getBatch().getExpiryDate() == null) continue;
            long days = ChronoUnit.DAYS.between(today, inventory.getBatch().getExpiryDate());
            if (days > expiryAheadDays()) continue;
            Product product = inventory.getProduct();
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("locationCode", inventory.getLocation().getCode());
            extra.put("itemKey", inventory.getBatch().getItemKey());
            extra.put("expiryDate", inventory.getBatch().getExpiryDate());
            extra.put("daysRemaining", days);
            extra.put("availableQty", inventory.getAvailableQty());
            rows.add(new AlertItem(
                    "EXPIRY:" + inventory.getId(), "EXPIRY", "PRODUCT",
                    product.getId(), product.getSku(), product.getName(),
                    null, null, null, null, inventory.getId(),
                    days <= 0 ? "HIGH" : "MEDIUM",
                    inventory.getQty(), null, product.getSafetyStock(),
                    null, null, null, null, null, null, product.getProcurementLeadDays(), false,
                    product.getName() + " 批次 " + inventory.getBatch().getItemKey()
                            + (days <= 0 ? " 已过期" : " 将于 " + inventory.getBatch().getExpiryDate() + " 到期（剩 " + days + " 天）")
                            + "，请优先出库。",
                    extra));
        }
        return rows;
    }

    /**
     * 库存预警：备件级补货点 = 安全库存 + 各设备补货周期内的需求之和。
     *
     * <p>原来只按单台设备算，通用件挂在 10 台设备上会低估十倍；现在按备件聚合，
     * 因此不再需要单独的设备级补货点。</p>
     */
    private List<AlertItem> stockAlerts(LocalDate today, Map<Long, Integer> stockByProduct,
                                        Map<Long, List<EquipmentPart>> linksByProduct,
                                        Map<Long, SparePartForecastVO> forecastByProduct,
                                        Set<Long> covered) {
        List<AlertItem> rows = new ArrayList<>();
        for (Product product : productRepo.findAll()) {
            if (!product.isEnabled()) continue;
            int stock = stockByProduct.getOrDefault(product.getId(), 0);
            List<EquipmentPart> links = linksByProduct.getOrDefault(product.getId(), List.of());
            int lead = product.getProcurementLeadDays() > 0 ? product.getProcurementLeadDays() : Math.max(defaultLeadDays(), 0);
            int leadDemand = leadDemand(links, lead);
            int safety = Math.max(product.getSafetyStock(), 0);
            int reorderPoint = safety + leadDemand;
            if (reorderPoint <= 0 || stock > reorderPoint) continue;
            covered.add(product.getId());

            SparePartForecastVO forecast = forecastByProduct.get(product.getId());
            Integer suggested = forecast == null ? null : forecast.suggestedQty();
            if (suggested == null || suggested <= 0) suggested = Math.max(reorderPoint - stock, 0);
            BigDecimal daily = forecast == null ? null : forecast.dailyDemand();
            Integer coverage = forecast == null ? null : forecast.coverageDays();

            boolean belowSafety = stock <= safety;
            String message = belowSafety
                    ? product.getName() + " 已低于安全库存（当前 " + stock + " / 安全库存 " + safety + "），建议补 "
                        + suggested + " 件，请及时采购。"
                    : product.getName() + " 已低于补货点（当前 " + stock + " / 补货点 " + reorderPoint + "），建议补 "
                        + suggested + " 件，请及时采购。";

            EquipmentPart tightest = tightest(links, lead);
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("reorderPoint", reorderPoint);
            if (tightest != null) {
                extra.put("installedQty", tightest.getInstalledQty());
                extra.put("changeCycleDays", tightest.getChangeCycleDays());
            }
            rows.add(new AlertItem(
                    "STOCK:" + product.getId(), "STOCK", "PRODUCT",
                    product.getId(), product.getSku(), product.getName(),
                    tightest == null ? null : tightest.getEquipment().getId(),
                    tightest == null ? null : tightest.getEquipment().getCode(),
                    tightest == null ? null : tightest.getEquipment().getName(),
                    null, null,
                    belowSafety ? "HIGH" : "MEDIUM",
                    stock, reorderPoint, safety, daily, coverage,
                    forecast == null ? null : forecast.stockoutDate(),
                    forecast == null ? today : forecast.receiveByDate(),
                    forecast == null ? null : forecast.orderByDate(),
                    suggested, product.getProcurementLeadDays(), product.getProcurementLeadDays() <= 0,
                    message, extra));
        }
        return rows;
    }

    /** 需求预测：库存还没触到补货点，但按当前消耗速度会在补货周期内见底 */
    private List<AlertItem> forecastAlerts(List<SparePartForecastVO> forecasts, Set<Long> covered) {
        List<AlertItem> rows = new ArrayList<>();
        for (SparePartForecastVO forecast : forecasts) {
            if (forecast.severity() == null) continue;
            if (covered.contains(forecast.productId())) continue;
            covered.add(forecast.productId());
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("actionHint", forecast.actionHint());
            extra.put("gaps", forecast.gaps());
            rows.add(new AlertItem(
                    "FORECAST:" + forecast.productId(), "FORECAST", "PRODUCT",
                    forecast.productId(), forecast.sku(), forecast.productName(),
                    null, null, null, null, null,
                    forecast.severity(), forecast.stockQty(), forecast.safetyStock(), forecast.safetyStock(),
                    forecast.dailyDemand(), forecast.coverageDays(),
                    forecast.stockoutDate(), forecast.receiveByDate(), forecast.orderByDate(),
                    forecast.suggestedQty(), forecast.leadDays(), forecast.leadEstimated(),
                    forecast.actionHint(), extra));
        }
        return rows;
    }

    /**
     * 更换预警：预计更换日 − 今天 ≤ 提前量。
     *
     * <p>提前量只用于安排检修，<b>不随采购周期放大</b>——换件需要的货由库存预测负责提前提醒。
     * 只有在库存已经支撑不了这次更换时才升级成 HIGH 并提示加急采购。</p>
     */
    private List<AlertItem> replaceAlerts(LocalDate today, Map<Long, Integer> stockByProduct,
                                          Map<Long, Product> productById) {
        List<AlertItem> rows = new ArrayList<>();
        for (SparePartUsage usage : usageRepo.findByStatusInOrderByIdDesc(List.of("IN_USE", "NEAR_DUE", "DUE"))) {
            Product product = productById.get(usage.getProductId());
            LocalDate replaceDate = usage.getExpectedReplaceAt() == null ? null
                    : usage.getExpectedReplaceAt().atZone(ZoneId.systemDefault()).toLocalDate();
            Integer daysLeft = replaceDate == null ? null : (int) ChronoUnit.DAYS.between(today, replaceDate);
            // 提前量：备件档案上单独设的优先（长寿命件想早点提醒就调大），没单独设（0）就用系统配置值。
            // 取备件档案的实时值而不是台账快照，改完立即生效，不用等台账刷新。
            int ahead = product != null && product.getLifeWarningDays() > 0 ? product.getLifeWarningDays()
                    : usage.getWarningDays() > 0 ? usage.getWarningDays() : lifeWarningDays();
            boolean due = daysLeft != null ? daysLeft <= ahead
                    : !"IN_USE".equals(usage.getStatus());
            if (!due) continue;

            int stock = stockByProduct.getOrDefault(usage.getProductId(), 0);
            int need = Math.max(usage.getQty(), 1);
            boolean overdue = daysLeft != null && daysLeft <= 0;
            boolean shortStock = stock < need;
            String severity = overdue || shortStock || "DUE".equals(usage.getStatus()) ? "HIGH" : "MEDIUM";

            String when = replaceDate == null ? "已到更换周期" : replaceDate.toString();
            String message = usage.getEquipmentName() == null
                    ? usage.getProductName() + " 预计 " + when + " 到期，请及时更换。"
                    : usage.getEquipmentName() + " 的 " + usage.getProductName() + " 预计 " + when + " 到期，请及时更换。";
            if (shortStock) {
                message = message + " 但当前库存 " + stock + " 不足以支撑本次更换（需 " + need + "），请加急采购。";
            }

            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("usageNo", usage.getUsageNo());
            extra.put("activeQty", usage.getActiveQty());
            extra.put("daysLeft", daysLeft);
            extra.put("usedPercent", usedPercent(usage));
            rows.add(new AlertItem(
                    "LIFECYCLE:" + usage.getId(), "LIFECYCLE", "DEVICE",
                    usage.getProductId(), usage.getSku(), usage.getProductName(),
                    usage.getEquipmentId(), usage.getEquipmentCode(), usage.getEquipmentName(),
                    usage.getId(), null,
                    severity, stock, need, product == null ? null : product.getSafetyStock(),
                    null, null, null, null, null, null,
                    product == null ? null : product.getProcurementLeadDays(), false,
                    message, extra));
        }
        return rows;
    }

    private Integer usedPercent(SparePartUsage usage) {
        LifeMetricUsageVO top = LifeMetrics.dominant(LifeMetrics.evaluate(usage,
                LifeMetrics.parse(usage.getLifeMetricRules(), usage.getLifeMetric(), usage.getRatedLifeValue())));
        return top == null ? null : top.usedPercent();
    }

    /** 各设备在补货周期内要换几次、每次几个；更换周期没填的设备不计 */
    static int leadDemand(List<EquipmentPart> links, int leadDays) {
        if (leadDays <= 0) return 0;
        int total = 0;
        for (EquipmentPart link : links) {
            Integer cycle = link.getChangeCycleDays();
            if (cycle == null || cycle <= 0) continue;
            int times = Math.max(1, (int) Math.ceil((double) leadDays / cycle));
            total += times * Math.max(link.getInstalledQty(), 0);
        }
        return total;
    }

    /** 换得最勤的那台设备，用于提醒里点名 */
    private static EquipmentPart tightest(List<EquipmentPart> links, int leadDays) {
        EquipmentPart best = null;
        int bestDemand = -1;
        for (EquipmentPart link : links) {
            int demand = leadDemand(List.of(link), leadDays);
            if (demand > bestDemand || (demand == bestDemand && best == null)) {
                best = link; bestDemand = demand;
            }
        }
        return best;
    }

    // ==================== 落表与站内消息 ====================

    /**
     * 把当前算出来的预警写进预警表。
     *
     * <p>同一预警对象（{@code dedupKey}）只保留一条：已处理的不再自动重开，
     * 免得现场每天被同一件事打扰；只有情况恶化（MEDIUM 升 HIGH）才重新打开。</p>
     *
     * @return 本次新增的条数
     */
    @Transactional
    public int snapshot() {
        LocalDate today = LocalDate.now();
        Instant now = Instant.now();
        int created = 0;
        for (AlertItem item : compute()) {
            Optional<SparePartAlert> existing = alertRepo.findTopByDedupKeyOrderByIdDesc(item.dedupKey());
            if (existing.isPresent()) {
                SparePartAlert row = existing.get();
                if (!"OPEN".equals(row.getStatus())) {
                    boolean worse = "HIGH".equals(item.severity()) && !"HIGH".equals(row.getSeverity());
                    if (!worse) continue;
                    row.setStatus("OPEN");
                    row.setHandlerId(null);
                    row.setHandlerName(null);
                    row.setHandledAt(null);
                }
                apply(row, item, today, now);
                continue;
            }
            SparePartAlert row = new SparePartAlert();
            row.setDedupKey(item.dedupKey());
            row.setStatus("OPEN");
            row.setCreatedAt(now);
            apply(row, item, today, now);
            created++;
        }
        return created;
    }

    private void apply(SparePartAlert row, AlertItem item, LocalDate today, Instant now) {
        row.setAlertDate(today);
        row.setAlertType(item.alertType());
        row.setScope(item.scope());
        row.setProductId(item.productId());
        row.setSku(item.sku());
        row.setProductName(item.productName());
        row.setEquipmentId(item.equipmentId());
        row.setEquipmentCode(item.equipmentCode());
        row.setEquipmentName(item.equipmentName());
        row.setUsageId(item.usageId());
        row.setInventoryId(item.inventoryId());
        row.setSeverity(item.severity());
        row.setStockQty(item.stockQty());
        row.setThresholdQty(item.thresholdQty());
        row.setSafetyStock(item.safetyStock());
        row.setDailyDemand(item.dailyDemand());
        row.setCoverageDays(item.coverageDays());
        row.setStockoutDate(item.stockoutDate());
        row.setReceiveByDate(item.receiveByDate());
        row.setOrderByDate(item.orderByDate());
        row.setSuggestedQty(item.suggestedQty());
        row.setProcurementLeadDays(item.procurementLeadDays());
        row.setLeadEstimated(item.leadEstimated());
        row.setMessage(item.message());
        row.setUpdatedAt(now);
        if (row.getCreatedAt() == null) row.setCreatedAt(now);
        alertRepo.save(row);
    }

    // ==================== 当前用户辅助 ====================

    /** 从 SecurityContext 获取当前登录用户，找不到返回 null */
    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        String username = String.valueOf(auth.getPrincipal());
        return userRepo.findByUsername(username).orElse(null);
    }

    /** 获取或创建某用户对某预警的状态记录 */
    private AlertUserState getOrCreateUserState(Long alertId, Long userId, Instant now) {
        return alertUserStateRepo.findByAlertIdAndUserId(alertId, userId)
                .orElseGet(() -> {
                    AlertUserState s = new AlertUserState();
                    s.setAlertId(alertId);
                    s.setUserId(userId);
                    s.setReadFlag(false);
                    s.setIgnored(false);
                    s.setCreatedAt(now);
                    s.setUpdatedAt(now);
                    return alertUserStateRepo.save(s);
                });
    }

    // ==================== 页面查询 ====================

    /** 页面读表：按当前用户视角过滤 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> inbox(String status) {
        User user = currentUser();
        Long userId = user == null ? null : user.getId();

        // 先查主表
        List<SparePartAlert> rows;
        if (status == null || status.isBlank() || "OPEN".equals(status)) {
            rows = alertRepo.findByStatusOrderBySeverityAscIdDesc("OPEN");
        } else {
            rows = alertRepo.findByStatusOrderBySeverityAscIdDesc(status);
        }

        // 如果有用户，过滤掉他忽略的 OPEN，并补充 readFlag/ignored 字段
        if (userId != null && (status == null || status.isBlank() || "OPEN".equals(status))) {
            Set<Long> ignoredIds = alertUserStateRepo.findByUserId(userId).stream()
                    .filter(AlertUserState::isIgnored)
                    .map(AlertUserState::getAlertId)
                    .collect(Collectors.toSet());
            rows = rows.stream().filter(r -> !ignoredIds.contains(r.getId())).toList();
        }

        // 批量查用户状态，避免 N+1
        Map<Long, AlertUserState> stateByAlert = new HashMap<>();
        if (userId != null && !rows.isEmpty()) {
            List<Long> alertIds = rows.stream().map(SparePartAlert::getId).toList();
            for (AlertUserState s : alertUserStateRepo.findByAlertIdInAndUserId(alertIds, userId)) {
                stateByAlert.put(s.getAlertId(), s);
            }
        }

        Map<Long, User> userMap = user == null ? Map.of()
                : userRepo.findAll().stream().collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        final Long currentUserId = userId;
        return rows.stream().map(r -> toMap(r, stateByAlert.get(r.getId()), currentUserId, userMap)).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        User user = currentUser();
        if (user == null) {
            // 未登录时保守返回 0
            return 0;
        }
        return alertUserStateRepo.countUnreadOpenAlerts(user.getId());
    }

    /** 当前用户可见的 OPEN 预警总数（排除已忽略的，读/未读都算），与 inbox() 口径一致 */
    @Transactional(readOnly = true)
    public long visibleOpenCount() {
        User user = currentUser();
        if (user == null) return alertRepo.countByStatus("OPEN");
        return alertUserStateRepo.countVisibleOpenAlerts(user.getId());
    }

    // ==================== 操作接口 ====================

    @Transactional
    public void markRead(Long id) {
        User user = currentUser();
        if (user == null) return;
        Instant now = Instant.now();
        AlertUserState s = getOrCreateUserState(id, user.getId(), now);
        if (!s.isReadFlag()) {
            s.setReadFlag(true);
            s.setUpdatedAt(now);
            alertUserStateRepo.save(s);
        }
    }

    @Transactional
    public void markAllRead() {
        User user = currentUser();
        if (user == null) return;
        Instant now = Instant.now();
        List<SparePartAlert> openAlerts = alertRepo.findByStatusOrderBySeverityAscIdDesc("OPEN");
        Set<Long> ignoredIds = alertUserStateRepo.findByUserId(user.getId()).stream()
                .filter(AlertUserState::isIgnored)
                .map(AlertUserState::getAlertId)
                .collect(Collectors.toSet());
        for (SparePartAlert alert : openAlerts) {
            if (ignoredIds.contains(alert.getId())) continue;
            AlertUserState s = getOrCreateUserState(alert.getId(), user.getId(), now);
            if (!s.isReadFlag()) {
                s.setReadFlag(true);
                s.setUpdatedAt(now);
                alertUserStateRepo.save(s);
            }
        }
    }

    /** 已处理：全局状态变为 ACK，记录处理人 */
    @Transactional
    public void acknowledge(Long id) {
        SparePartAlert row = alertRepo.findById(id).orElseThrow(() -> new com.wms.common.BizException("预警记录不存在"));
        row.setStatus("ACK");
        User user = currentUser();
        String operatorName = user != null ? (user.getDisplayName() != null && !user.getDisplayName().isBlank()
                ? user.getDisplayName() : user.getUsername()) : null;
        if (user != null) {
            row.setHandlerId(user.getId());
            row.setHandlerName(operatorName);
        }
        row.setHandledAt(Instant.now());
        row.setUpdatedAt(Instant.now());
        alertRepo.save(row);
        auditService.record("ALERT", row.getId(), row.getSku(), "ACKNOWLEDGE", "OPEN", "ACK", operatorName,
                "预警已处理：" + row.getMessage());
    }

    /** 忽略：只影响当前用户的视图，写入用户维度状态 */
    @Transactional
    public void ignore(Long id) {
        User user = currentUser();
        if (user == null) return;
        Instant now = Instant.now();
        AlertUserState s = getOrCreateUserState(id, user.getId(), now);
        if (!s.isIgnored()) {
            s.setIgnored(true);
            s.setReadFlag(true); // 忽略同时视为已读
            s.setUpdatedAt(now);
            alertUserStateRepo.save(s);
        }
        SparePartAlert row = alertRepo.findById(id).orElse(null);
        String sku = row != null ? row.getSku() : null;
        String operatorName = user.getDisplayName() != null && !user.getDisplayName().isBlank()
                ? user.getDisplayName() : user.getUsername();
        auditService.record("ALERT", id, sku, "IGNORE", null, null, operatorName,
                "用户忽略预警（仅影响自己视图）：" + (row != null ? row.getMessage() : ""));
    }

    // ==================== DTO 映射 ====================

    /** toMap 增加用户维度的 readFlag/ignored，以及处理人信息 */
    private static Map<String, Object> toMap(SparePartAlert row, AlertUserState userState,
                                              Long currentUserId, Map<Long, User> userMap) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.getId());
        m.put("alertDate", row.getAlertDate());
        m.put("alertType", row.getAlertType());
        m.put("scope", row.getScope());
        m.put("productId", row.getProductId());
        m.put("sku", row.getSku());
        m.put("productName", row.getProductName());
        m.put("equipmentId", row.getEquipmentId());
        m.put("equipmentCode", row.getEquipmentCode());
        m.put("equipmentName", row.getEquipmentName());
        m.put("usageId", row.getUsageId());
        m.put("inventoryId", row.getInventoryId());
        m.put("severity", row.getSeverity());
        m.put("stockQty", row.getStockQty());
        m.put("thresholdQty", row.getThresholdQty());
        m.put("safetyStock", row.getSafetyStock());
        m.put("dailyDemand", row.getDailyDemand());
        m.put("coverageDays", row.getCoverageDays());
        m.put("stockoutDate", row.getStockoutDate());
        m.put("receiveByDate", row.getReceiveByDate());
        m.put("orderByDate", row.getOrderByDate());
        m.put("suggestedQty", row.getSuggestedQty());
        m.put("procurementLeadDays", row.getProcurementLeadDays());
        m.put("leadEstimated", row.isLeadEstimated());
        m.put("message", row.getMessage());
        m.put("status", row.getStatus());
        // 用户维度状态：未创建过状态记录时默认 false
        boolean readFlag = userState != null && userState.isReadFlag();
        boolean ignored = userState != null && userState.isIgnored();
        m.put("readFlag", readFlag);
        m.put("ignored", ignored);
        // 处理人信息（ACK 状态才有）
        m.put("handlerId", row.getHandlerId());
        m.put("handlerName", row.getHandlerName());
        m.put("handledAt", row.getHandledAt());
        m.put("updatedAt", row.getUpdatedAt());
        return m;
    }
}
