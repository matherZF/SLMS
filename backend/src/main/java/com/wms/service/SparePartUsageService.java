package com.wms.service;

import com.wms.common.BizException;
import com.wms.dto.*;
import com.wms.entity.*;
import com.wms.repository.EquipmentPartRepository;
import com.wms.repository.SparePartUsageRepository;
import com.wms.repository.SparePartUsageSnapshotRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SparePartUsageService {

    private static final Set<String> ENDED_STATUS = Set.of("RETURNED", "CLOSED");

    private final SparePartUsageRepository repo;
    private final SparePartUsageSnapshotRepository snapshotRepo;
    private final EquipmentPartRepository partRepo;
    private final ObjectProvider<DeviceRuntimeViewService> runtimeProvider;
    private final DeviceCumulativeService cumulative;
    private final OperationAuditService audit;
    private final SystemConfigService configService;
    private final com.wms.repository.ProductRepository productRepo;
    /** 拟合日均消耗所需的最短样本跨度（天）；不足则只显示已积累天数，不预测 */
    private final int forecastMinDays;

    public SparePartUsageService(SparePartUsageRepository r,
                                 SparePartUsageSnapshotRepository sr,
                                 EquipmentPartRepository pr,
                                 ObjectProvider<DeviceRuntimeViewService> p,
                                 DeviceCumulativeService cumulative,
                                 OperationAuditService a,
                                 SystemConfigService configService,
                                 com.wms.repository.ProductRepository productRepo,
                                 @Value("${spare-part.usage-sync.forecast-min-days:3}") int forecastMinDays) {
        this.repo = r;
        this.snapshotRepo = sr;
        this.partRepo = pr;
        this.runtimeProvider = p;
        this.cumulative = cumulative;
        this.audit = a;
        this.configService = configService;
        this.productRepo = productRepo;
        this.forecastMinDays = forecastMinDays;
    }

    /** 样本跨度阈值可在系统配置里改 */
    private int forecastMinDays() { return configService.forecastMinDays() > 0 ? configService.forecastMinDays() : forecastMinDays; }

    /**
     * 备件对应的装机设备：由基础数据的「设备-备件关联」决定，出库单不再单独选设备。
     *
     * @param equipment  唯一关联的设备；无关联或关联多台时为 null
     * @param cycleDays  该关联上填的更换周期，随台账快照下来用于推算计划更换日
     * @param issue      未绑定时的原因，写进台账 dataError 供页面展示
     */
    private record DeviceBinding(Equipment equipment, Integer cycleDays, String issue) {
    }

    private DeviceBinding resolveBinding(Long productId) {
        Map<Long, EquipmentPart> unique = new LinkedHashMap<>();
        for (EquipmentPart link : partRepo.findByProductId(productId)) {
            Equipment device = link.getEquipment();
            if (device == null) continue;
            unique.putIfAbsent(device.getId(), link);
        }
        if (unique.isEmpty()) {
            return new DeviceBinding(null, null, "该备件未在基础数据中关联设备");
        }
        if (unique.size() > 1) {
            String codes = unique.values().stream().map(link -> link.getEquipment().getCode())
                    .limit(5).collect(Collectors.joining("、"));
            return new DeviceBinding(null, null,
                    "该备件关联了 " + unique.size() + " 台设备（" + codes + "），无法自动确定使用设备");
        }
        EquipmentPart only = unique.values().iterator().next();
        return new DeviceBinding(only.getEquipment(), only.getChangeCycleDays(), null);
    }

    @Transactional
    public SparePartUsage createFromOutbound(OutboundOrder order, OutboundItem item, Instant startedAt) {
        if (repo.existsByOutboundItemId(item.getId())) return repo.findByOutboundItemId(item.getId()).orElseThrow();
        Product p = item.getProduct();
        SparePartUsage u = new SparePartUsage();
        u.setUsageNo("USE-" + order.getOrderNo() + "-" + item.getId());
        u.setOutboundOrderId(order.getId());
        u.setOutboundOrderNo(order.getOrderNo());
        u.setOutboundItemId(item.getId());
        u.setProductId(p.getId());
        u.setSku(text(item.getProductSkuSnapshot(), p.getSku()));
        u.setProductName(text(item.getProductNameSnapshot(), p.getName()));
        u.setSpec(item.getProductSpecSnapshot() != null ? item.getProductSpecSnapshot() : p.getSpec());
        u.setUnit(item.getProductUnitSnapshot() != null ? item.getProductUnitSnapshot() : p.getUnit());
        if (item.getBatch() != null) {
            u.setBatchId(item.getBatch().getId());
            u.setBatchNo(item.getBatch().getItemKey());
        }
        u.setQty(item.getPickedQty());
        u.setActiveQty(item.getPickedQty());
        u.setReturnedQty(0);
        // 使用设备先取这条明细上指定的那台：一张领用单可以有多个备件、装在不同设备上，
        // 单头那个值只在整单同一台时才可信，不能拿来给所有明细用。
        // 明细没指定才退回单头，再退回基础数据的唯一关联；仍无法确定时台账先挂起，页面显示「未绑定设备」。
        DeviceBinding binding = resolveBinding(p.getId());
        Equipment e = item.getEquipment() != null ? item.getEquipment()
                : order.getEquipment() != null ? order.getEquipment() : binding.equipment();
        if (e != null) {
            u.setEquipmentId(e.getId());
            u.setEquipmentCode(e.getCode());
            u.setEquipmentName(e.getName());
        }
        // 多口径规则在使用台账建立时快照下来：之后主数据改额定值，不会追溯影响已发出的备件
        List<LifeMetricRule> rules = LifeMetrics.parse(p.getLifeMetricRules(), p.getLifeMetric(), p.getRatedLifeValue());
        u.setLifeMetricRules(LifeMetrics.toJson(rules));
        applyPrimaryMetric(u, rules);
        u.setWarningDays(p.getLifeWarningDays());
        u.setStartedAt(startedAt);
        // 更换周期统一按「这一台 × 这一个备件」这条关联取，避免领用单换了台设备却沿用别处的周期
        u.setPlannedCycleDays(lookupCycleDays(u));
        establishBaseline(u, e != null ? null : binding.issue());
        // 出库这一刻就能给出纯天数口径的到期日与计划更换日；其余口径要等快照攒够跨度
        applyForecast(u, null);
        repo.save(u);
        recordSnapshot(u, "OUTBOUND");
        audit.record("SPARE_USAGE", u.getId(), u.getUsageNo(), "CREATE", null, u.getStatus(),
                "出库完成自动建立备件使用台账；领用单：" + order.getOrderNo());
        return u;
    }

    public Page<SparePartUsageVO> page(String status, String keyword, int page, int size) {
        String s = StringUtils.hasText(status) ? status.trim().toUpperCase() : null;
        String k = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return repo.search(s, k, PageRequest.of(page, size, Sort.by("id").descending())).map(this::toVO);
    }

    public SparePartUsageVO detail(Long id) {
        SparePartUsage u = repo.findById(id).orElseThrow(() -> new BizException("备件使用记录不存在"));
        List<LifeMetricRule> rules = rulesOf(u);
        return toVO(u, ratesOf(u, rules));
    }

    public List<SparePartUsageSnapshotVO> snapshots(Long id) {
        if (!repo.existsById(id)) throw new BizException("备件使用记录不存在");
        return snapshotRepo.findTop100ByUsageIdOrderByCollectedAtDesc(id).stream().map(this::toSnapshotVO).toList();
    }

    @Transactional(noRollbackFor = BizException.class)
    public SparePartUsageVO refresh(Long id) {
        SparePartUsage u = repo.findById(id).orElseThrow(() -> new BizException("备件使用记录不存在"));
        if (ENDED_STATUS.contains(u.getStatus())) throw new BizException("已结束的使用台账不能刷新");
        if (rulesOf(u).isEmpty()) return toVO(u);
        String previous = u.getStatus();
        try {
            Map<String, LifeMetrics.Rate> rates = refreshOne(u, "MANUAL");
            audit.record("SPARE_USAGE", u.getId(), u.getUsageNo(), "REFRESH", previous, u.getStatus(), "人工刷新备件寿命数据");
            return toVO(u, rates);
        } catch (Exception e) {
            String msg = e instanceof BizException ? e.getMessage() : "读取设备运行数据失败：" + e.getMessage();
            u.setDataError(msg);
            repo.save(u);
            throw new BizException(msg);
        }
    }

    @Transactional
    public Map<String, Integer> refreshAll(String source) {
        List<SparePartUsage> list = repo.findTrackable();
        int success = 0, failed = 0;
        for (SparePartUsage u : list) {
            try {
                refreshOne(u, source);
                success++;
            } catch (Exception e) {
                u.setDataError(e.getMessage());
                repo.save(u);
                failed++;
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("total", list.size());
        result.put("success", success);
        result.put("failed", failed);
        if (!list.isEmpty()) {
            audit.record("SPARE_USAGE", 0L, "BATCH", "BATCH_REFRESH", null, null,
                    ("SCHEDULED".equals(source) ? "夜间自动" : "人工批量") + "刷新；成功：" + success + "，失败：" + failed);
        }
        return result;
    }

    @Transactional
    public void applyReturn(Long outboundItemId, int returnQty, String returnNo, String reason) {
        repo.findByOutboundItemId(outboundItemId).ifPresent(u -> {
            int remaining = u.getQty() - u.getReturnedQty();
            if (returnQty > remaining) throw new BizException("退库数量超过使用台账可退数量 " + remaining);
            String previous = u.getStatus();
            u.setReturnedQty(Math.addExact(u.getReturnedQty(), returnQty));
            u.setActiveQty(Math.max(0, u.getActiveQty() - returnQty));
            if (u.getReturnedQty() == u.getQty()) {
                u.setStatus("RETURNED");
                u.setEndedAt(Instant.now());
                u.setEndReason("全部退库；" + reason);
            }
            repo.save(u);
            recordSnapshot(u, "RETURN");
            audit.record("SPARE_USAGE", u.getId(), u.getUsageNo(), "RETURN", previous, u.getStatus(),
                    "关联退库 " + returnNo + "；数量：" + returnQty + "；当前在用：" + u.getActiveQty());
        });
    }

    @Transactional
    public SparePartUsageVO end(Long id, String reason) {
        SparePartUsage u = repo.findById(id).orElseThrow(() -> new BizException("备件使用记录不存在"));
        if (ENDED_STATUS.contains(u.getStatus())) throw new BizException("该使用台账已经结束");
        if (!StringUtils.hasText(reason)) throw new BizException("结束原因不能为空");
        String clean = reason.trim();
        if (clean.length() > 500) throw new BizException("结束原因不能超过500个字符");
        String previous = u.getStatus();
        u.setStatus("CLOSED");
        u.setActiveQty(0);
        u.setEndedAt(Instant.now());
        u.setEndReason(clean);
        repo.save(u);
        recordSnapshot(u, "CLOSE");
        audit.record("SPARE_USAGE", u.getId(), u.getUsageNo(), "CLOSE", previous, u.getStatus(), "结束备件使用跟踪；原因：" + clean);
        return toVO(u);
    }

    /* ---------------- 寿命计算 ---------------- */

    private List<LifeMetricRule> rulesOf(SparePartUsage u) {
        return LifeMetrics.parse(u.getLifeMetricRules(), u.getLifeMetric(), u.getRatedLifeValue());
    }

    /** 把规则第一条写回旧字段，保持列表、导出、告警等既有读取路径可用 */
    private void applyPrimaryMetric(SparePartUsage u, List<LifeMetricRule> rules) {
        if (rules.isEmpty()) {
            u.setLifeMetric("NONE");
            u.setRatedLifeValue(null);
            return;
        }
        LifeMetricRule primary = rules.get(0);
        u.setLifeMetric(primary.metric());
        u.setRatedLifeValue(primary.rated());
    }

    /** 刷新一条台账，返回本次拟合出的各口径速率供调用方直接展示 */
    private Map<String, LifeMetrics.Rate> refreshOne(SparePartUsage u, String source) {
        List<LifeMetricRule> rules = rulesOf(u);
        String bindIssue = reattachEquipmentIfMissing(u);
        // 速率用"本次刷新之前"的快照序列拟合：本次刚采到的点还没产生增量，混进来只会稀释斜率
        Map<String, LifeMetrics.Rate> rates = ratesOf(u, rules);
        // 不涉及设备累计数据的口径（NONE / 纯自然天数）无需连设备库
        if (!LifeMetrics.needsDeviceData(rules)) {
            u.setLastSyncedAt(Instant.now());
            u.setDataError(bindIssue);
            updateStatus(u);
            applyForecast(u, rates);
            repo.save(u);
            recordSnapshot(u, source);
            return rates;
        }
        DeviceSnapshot snap = loadDeviceData(u, rules);
        validateRuntime(u, snap, rules);
        if ("PENDING".equals(u.getBaselineStatus())) {
            applyBaseline(u, snap);
            u.setBaselineStatus("READY");
        }
        if (snap.runningHours() != null) u.setCurrentRunningHours(snap.runningHours());
        if (snap.taskCount() != null) u.setCurrentTaskCount(snap.taskCount());
        if (snap.pieceQty() != null) u.setCurrentPieceQty(snap.pieceQty());
        u.setLastSyncedAt(Instant.now());
        u.setDataError(bindIssue);
        updateStatus(u);
        applyForecast(u, rates);
        repo.save(u);
        recordSnapshot(u, source);
        return rates;
    }

    /**
     * 补挂设备：台账建单那一刻备件可能还没在基础数据里关联设备，device 字段就是空的。
     * 关联后补上时，刷新要按当前基础数据重新认一次，否则这类老台账会永远停在「未绑定设备」。
     * 已经有设备的台账原样保留（出库当时装在哪台是既成事实，不追溯改写）。
     *
     * @return 仍然无法确定设备时的原因；成功补挂或本就有设备返回 null
     */
    private String reattachEquipmentIfMissing(SparePartUsage u) {
        if (u.getEquipmentId() != null || u.getProductId() == null) return null;
        DeviceBinding binding = resolveBinding(u.getProductId());
        if (binding.equipment() == null) return binding.issue();
        Equipment e = binding.equipment();
        u.setEquipmentId(e.getId());
        u.setEquipmentCode(e.getCode());
        u.setEquipmentName(e.getName());
        return null;
    }

    /** 该台账可用的消耗速率；纯天数口径或尚无快照时返回空表 */
    private Map<String, LifeMetrics.Rate> ratesOf(SparePartUsage u, List<LifeMetricRule> rules) {
        if (u.getId() == null || !LifeMetrics.needsDeviceData(rules)) return Map.of();
        return LifeMetrics.rates(
                snapshotRepo.findTop100ByUsageIdOrderByCollectedAtDesc(u.getId()), rules, forecastMinDays());
    }

    /**
     * 写入更换预测：日均消耗 → 寿命耗尽日，再与计划更换日取早。
     *
     * <p>更换周期优先用出库时快照的值，历史台账（V18 之前建的）没有快照就从当前设备关联补查，
     * 保证老数据刷新一次也能出预测。</p>
     */
    private void applyForecast(SparePartUsage u, Map<String, LifeMetrics.Rate> rates) {
        List<LifeMetricRule> rules = rulesOf(u);
        if (rules.isEmpty()) return;
        // 更换日按"谁先耗尽"取，不是按"谁用得最多"取：天数口径常常比例低但日期最先到
        LifeMetricUsageVO soonest = LifeMetrics.soonest(LifeMetrics.evaluate(u, rules, rates, Instant.now()));
        u.setDailyRate(soonest == null ? null : soonest.dailyRate());
        u.setRateSampleDays(soonest == null ? null : soonest.rateSampleDays());
        u.setExpectedEndAt(soonest == null ? null : soonest.expectedEndAt());
        if (u.getPlannedCycleDays() == null || u.getPlannedCycleDays() <= 0) {
            u.setPlannedCycleDays(lookupCycleDays(u));
        }
        u.setPlannedEndAt(plannedEndOf(u));
        u.setExpectedReplaceAt(LifeMetrics.earliest(u.getExpectedEndAt(), u.getPlannedEndAt()));
    }

    /** 从设备-备件关联补查更换周期；设备已解绑或没填时返回 null */
    private Integer lookupCycleDays(SparePartUsage u) {
        if (u.getEquipmentId() == null || u.getProductId() == null) return null;
        return partRepo.findByEquipmentIdAndProductId(u.getEquipmentId(), u.getProductId())
                .map(EquipmentPart::getChangeCycleDays)
                .filter(days -> days != null && days > 0)
                .orElse(null);
    }

    private Instant plannedEndOf(SparePartUsage u) {
        Integer cycle = u.getPlannedCycleDays();
        if (cycle == null || cycle <= 0 || u.getStartedAt() == null) return null;
        return u.getStartedAt().plus(Duration.ofDays(cycle));
    }

    /**
     * 按规则涉及的口径取设备数据。
     * 只配了 QTY 口径时，即使设备运行视图没启用/查不到这台设备，也能走每日汇总表取件数。
     */
    private DeviceSnapshot loadDeviceData(SparePartUsage u, List<LifeMetricRule> rules) {
        if (!StringUtils.hasText(u.getEquipmentCode())) throw new BizException("使用记录未绑定设备：该备件在基础数据中未关联设备，或关联了多台设备");
        String code = u.getEquipmentCode();
        DeviceRuntimeViewService view = runtimeProvider.getIfAvailable();
        DeviceRuntimeViewVO row = null;
        RuntimeException viewError = null;
        if (view != null) {
            try {
                row = view.findByEquipmentNo(code);
            } catch (RuntimeException e) {
                viewError = e;
            }
        }
        // 三个口径都可以退到每日汇总表：源系统可能只给产量、也可能只给时长，
        // 甚至只对部分设备给这些字段，所以每个口径各自判断有没有数，而不是整体失败。
        Long pieceQty = null;
        if (LifeMetrics.needsPieceQty(rules)) {
            pieceQty = row == null ? null : row.totalPieceQty();
            if (pieceQty == null) pieceQty = cumulative.cumulativeQty(code);
        }
        BigDecimal hours = null;
        Long tasks = null;
        if (LifeMetrics.needsRuntimeView(rules)) {
            hours = row == null ? null : row.totalRunningHours();
            tasks = row == null ? null : row.totalTaskCount();
            if (hours == null) hours = cumulative.cumulativeRunningHours(code);
            if (tasks == null) tasks = cumulative.cumulativeTaskCount(code);
            if (hours == null && tasks == null && view == null) {
                throw new BizException("设备运行数据源未启用，且每日汇总表里没有 " + code + " 的运行时长/任务次数");
            }
            if (viewError != null && row == null) {
                throw new BizException("读取设备运行数据失败：" + viewError.getMessage());
            }
        }
        return new DeviceSnapshot(hours, tasks, pieceQty);
    }

    private void validateRuntime(SparePartUsage u, DeviceSnapshot snap, List<LifeMetricRule> rules) {
        for (LifeMetricRule rule : rules) {
            switch (rule.metric()) {
                case "HOURS" -> {
                    if (snap.runningHours() == null) throw new BizException("设备累计运行时长为空");
                    if (u.getBaselineRunningHours() != null && snap.runningHours().compareTo(u.getBaselineRunningHours()) < 0)
                        throw new BizException("设备累计运行时长小于使用基准，请检查设备数据是否重置");
                }
                case "TASKS" -> {
                    if (snap.taskCount() == null) throw new BizException("设备累计任务数为空");
                    if (u.getBaselineTaskCount() != null && snap.taskCount() < u.getBaselineTaskCount())
                        throw new BizException("设备累计任务数小于使用基准，请检查设备数据是否重置");
                }
                case "QTY" -> {
                    if (snap.pieceQty() == null)
                        throw new BizException("设备累计处理件数为空，请确认设备每日运行数据已同步（ext-stat）");
                    if (u.getBaselinePieceQty() != null && snap.pieceQty() < u.getBaselinePieceQty())
                        throw new BizException("设备累计处理件数小于使用基准，请检查设备数据是否重置");
                }
                default -> { /* DAYS 口径不需要设备数据 */ }
            }
        }
    }

    private void applyBaseline(SparePartUsage u, DeviceSnapshot snap) {
        if (snap.runningHours() != null) {
            u.setBaselineRunningHours(snap.runningHours());
            u.setCurrentRunningHours(snap.runningHours());
        }
        if (snap.taskCount() != null) {
            u.setBaselineTaskCount(snap.taskCount());
            u.setCurrentTaskCount(snap.taskCount());
        }
        if (snap.pieceQty() != null) {
            u.setBaselinePieceQty(snap.pieceQty());
            u.setCurrentPieceQty(snap.pieceQty());
        }
    }

    private void establishBaseline(SparePartUsage u, String unboundIssue) {
        List<LifeMetricRule> rules = rulesOf(u);
        if (rules.isEmpty()) {
            u.setBaselineStatus("NOT_REQUIRED");
            u.setStatus("NOT_TRACKED");
            return;
        }
        // 纯自然天数口径不需要设备基准，任何设备都能算
        if (!LifeMetrics.needsDeviceData(rules)) {
            u.setBaselineStatus("READY");
            u.setStatus("IN_USE");
            return;
        }
        if (!StringUtils.hasText(u.getEquipmentCode())) {
            u.setBaselineStatus("PENDING");
            u.setStatus("BASELINE_PENDING");
            u.setDataError(StringUtils.hasText(unboundIssue) ? unboundIssue : "使用记录未绑定设备");
            return;
        }
        try {
            DeviceSnapshot snap = loadDeviceData(u, rules);
            applyBaseline(u, snap);
            u.setBaselineStatus("READY");
            u.setStatus("IN_USE");
            u.setLastSyncedAt(Instant.now());
        } catch (Exception e) {
            u.setBaselineStatus("PENDING");
            u.setStatus("BASELINE_PENDING");
            u.setDataError(e.getMessage());
        }
    }

    /**
     * 状态判定：<b>按距到期还剩几天</b>，不再按已用百分比。
     *
     * <p>百分比判据的提前量会随寿命长短漂移——一年寿命的件到 80% 还剩 73 天就开始报，
     * 10 天寿命的件到 80% 只剩 2 天，现场没法按同一节奏安排。改成天数后长短寿命口径一致。</p>
     */
    private void updateStatus(SparePartUsage u) {
        if (ENDED_STATUS.contains(u.getStatus())) return;
        Integer daysLeft = daysUntilReplace(u);
        if (daysLeft == null) {
            u.setStatus("IN_USE");
            return;
        }
        u.setStatus(daysLeft <= 0 ? "DUE" : daysLeft <= aheadDays(u) ? "NEAR_DUE" : "IN_USE");
    }

    /**
     * 提前量：备件档案上单独设的优先，没有（0）就看台账建档时的快照，再没有就用系统配置。
     * 与 {@code AlertService} 取同一套值，避免台账列表显示"在用"、预警却已经报出来了。
     */
    private int aheadDays(SparePartUsage u) {
        Product p = productRepo.findById(u.getProductId()).orElse(null);
        if (p != null && p.getLifeWarningDays() > 0) return p.getLifeWarningDays();
        if (u.getWarningDays() > 0) return u.getWarningDays();
        return configService.lifeWarningDays();
    }

    /**
     * 距预计更换日还有几天。
     *
     * <p>优先用台账上已经算好的预计更换日；还没算出来时（快照样本不足）用
     * 「剩余寿命 ÷ 日均消耗」折算。两个都给不出时返回 null，表示暂时无法判断，状态维持在用。</p>
     */
    private Integer daysUntilReplace(SparePartUsage u) {
        Instant expected = u.getExpectedReplaceAt();
        if (expected == null) {
            LifeMetricUsageVO top = LifeMetrics.dominant(LifeMetrics.evaluate(u, rulesOf(u)));
            if (top == null || top.remainingLifeValue() == null) return null;
            BigDecimal rate = top.dailyRate();
            if (rate == null || rate.signum() <= 0) return null;
            BigDecimal days = top.remainingLifeValue().divide(rate, 0, RoundingMode.HALF_UP);
            return days.intValue();
        }
        return (int) java.time.temporal.ChronoUnit.DAYS.between(
                LocalDate.now(), expected.atZone(ZoneId.systemDefault()).toLocalDate());
    }

    private void recordSnapshot(SparePartUsage u, String source) {
        LifeMetricUsageVO top = LifeMetrics.dominant(LifeMetrics.evaluate(u, rulesOf(u)));
        SparePartUsageSnapshot s = new SparePartUsageSnapshot();
        s.setUsageId(u.getId());
        s.setSource(source);
        s.setRunningHours(u.getCurrentRunningHours());
        s.setTaskCount(u.getCurrentTaskCount());
        s.setPieceQty(u.getCurrentPieceQty());
        s.setLifeMetric(top == null ? u.getLifeMetric() : top.metric());
        s.setUsedLifeValue(top == null ? null : top.usedLifeValue());
        s.setRemainingLifeValue(top == null ? null : top.remainingLifeValue());
        s.setUsedPercent(top == null ? null : top.usedPercent());
        s.setStatus(u.getStatus());
        s.setCollectedAt(Instant.now());
        snapshotRepo.save(s);
    }

    /**
     * 列表路径：预测字段取上次刷新落库的值，不查快照表。
     * 详情路径传 liveRates，用实时拟合的速率覆盖，保证明细与表头一致。
     */
    private SparePartUsageVO toVO(SparePartUsage u) {
        return toVO(u, null);
    }

    private SparePartUsageVO toVO(SparePartUsage u, Map<String, LifeMetrics.Rate> liveRates) {
        List<LifeMetricUsageVO> rows = LifeMetrics.evaluate(u, rulesOf(u), liveRates, Instant.now());
        LifeMetricUsageVO top = LifeMetrics.dominant(rows);
        LifeMetricUsageVO soonest = LifeMetrics.soonest(rows);
        boolean live = liveRates != null;
        Instant expectedEnd = live ? (soonest == null ? null : soonest.expectedEndAt()) : u.getExpectedEndAt();
        Instant plannedEnd = live ? plannedEndOf(u) : u.getPlannedEndAt();
        Instant replaceAt = live ? LifeMetrics.earliest(expectedEnd, plannedEnd) : u.getExpectedReplaceAt();
        return new SparePartUsageVO(u.getId(), u.getUsageNo(), u.getOutboundOrderId(), u.getOutboundOrderNo(),
                u.getOutboundItemId(), u.getProductId(), u.getSku(), u.getProductName(), u.getSpec(), u.getUnit(),
                u.getBatchId(), u.getBatchNo(), u.getQty(), u.getActiveQty(), u.getReturnedQty(),
                u.getEquipmentId(), u.getEquipmentCode(), u.getEquipmentName(),
                top == null ? u.getLifeMetric() : top.metric(),
                top == null ? null : top.ratedLifeValue(),
                u.getWarningPercent(), u.getWarningDays(),
                u.getBaselineRunningHours(), u.getBaselineTaskCount(), u.getCurrentRunningHours(), u.getCurrentTaskCount(),
                top == null ? null : top.usedLifeValue(),
                top == null ? null : top.remainingLifeValue(),
                top == null ? null : top.usedPercent(),
                u.getStatus(), u.getBaselineStatus(), u.getDataError(),
                u.getStartedAt(), u.getLastSyncedAt(), u.getEndedAt(), u.getEndReason(), u.getCreatedAt(),
                rows, u.getBaselinePieceQty(), u.getCurrentPieceQty(),
                live ? (soonest == null ? null : soonest.dailyRate()) : u.getDailyRate(),
                live ? (soonest == null ? null : soonest.rateSampleDays()) : u.getRateSampleDays(),
                expectedEnd, u.getPlannedCycleDays(), plannedEnd, replaceAt);
    }

    private SparePartUsageSnapshotVO toSnapshotVO(SparePartUsageSnapshot s) {
        return new SparePartUsageSnapshotVO(s.getId(), s.getSource(), s.getRunningHours(), s.getTaskCount(),
                s.getUsedLifeValue(), s.getRemainingLifeValue(), s.getUsedPercent(), s.getStatus(), s.getCollectedAt(),
                s.getLifeMetric(), s.getPieceQty());
    }

    private String text(String a, String b) {
        return StringUtils.hasText(a) ? a : b;
    }

    private record DeviceSnapshot(BigDecimal runningHours, Long taskCount, Long pieceQty) {
    }
}
