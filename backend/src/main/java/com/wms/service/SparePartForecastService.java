package com.wms.service;

import com.wms.dto.LifeMetricRule;
import com.wms.dto.SparePartForecastVO;
import com.wms.entity.DeviceStatDaily;
import com.wms.entity.Equipment;
import com.wms.entity.EquipmentPart;
import com.wms.entity.Inventory;
import com.wms.entity.Product;
import com.wms.repository.DeviceStatDailyRepository;
import com.wms.repository.EquipmentPartRepository;
import com.wms.repository.InventoryRepository;
import com.wms.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 备件需求预测。
 *
 * <p>立项材料要的是「故障码 → 备件消耗」的关联规则，但源系统给不出故障码与维修工单。
 * 能用的是每日运行汇总（{@code dws_device_daily_stat}）：设备号、日期、运行时长、任务次数、处理件数。
 * 其中运行时长只有<b>部分设备</b>有数（立项目标里也是"部分可得"），所以走的是
 * <b>运行强度驱动磨损</b>：设备干了多少活 → 备件磨掉多少 → 需要补多少。</p>
 *
 * <p>单机日耗取下面几个口径里<b>最大</b>的那个——谁先磨完就按谁补，
 * 与台账"建议更换日取早"是同一套逻辑：</p>
 * <pre>
 * 单机数量 × 设备日均运行小时 ÷ 额定运行小时    // HOURS 口径，设备得真报时长
 * 单机数量 × 设备日均任务次数 ÷ 额定任务次数    // TASKS 口径
 * 单机数量 × 设备日均处理件数 ÷ 额定处理件数    // QTY 口径
 * 单机数量 ÷ 更换周期                          // 管理上定的节奏
 * 单机数量 ÷ 额定使用天数                      // DAYS 口径
 * 备件日耗 = Σ 各设备的单机日耗（只算真在干活的）
 * </pre>
 *
 * <p>三个设备侧口径彼此独立：某台设备不报时长，就从另外两个口径算，不会因为一个字段缺失
 * 把整台设备判成不可预测。</p>
 */
@Service
public class SparePartForecastService {

    private final ProductRepository productRepo;
    private final EquipmentPartRepository partRepo;
    private final InventoryRepository inventoryRepo;
    private final DeviceStatDailyRepository statRepo;

    /** 用最近多少天的设备运行数据算日均；太短会被单日异常放大，太长跟不上产线变化。可在系统配置里改 */
    private final int fallbackProductionWindow;
    /** 除采购周期外额外保留几天的量（到货前不至于见底） */
    private final int fallbackSafetyDays;
    /** 备件没填采购周期时按几天估，避免把「不知道交期」算成「不用补」 */
    private final int fallbackDefaultLead;
    private final SystemConfigService configService;

    public SparePartForecastService(ProductRepository productRepo,
                                    EquipmentPartRepository partRepo,
                                    InventoryRepository inventoryRepo,
                                    DeviceStatDailyRepository statRepo,
                                    SystemConfigService configService,
                                    @Value("${spare-part.forecast.production-window-days:7}") int productionWindowDays,
                                    @Value("${spare-part.forecast.safety-days:7}") int safetyDays,
                                    @Value("${spare-part.forecast.default-lead-days:7}") int defaultLeadDays) {
        this.productRepo = productRepo;
        this.partRepo = partRepo;
        this.inventoryRepo = inventoryRepo;
        this.statRepo = statRepo;
        this.configService = configService;
        this.fallbackProductionWindow = productionWindowDays;
        this.fallbackSafetyDays = safetyDays;
        this.fallbackDefaultLead = defaultLeadDays;
    }

    /** 系统配置优先，配置缺失或为 0 时回落到环境变量里的出厂默认值 */
    private int productionWindowDays() { int v = configService.productionWindowDays(); return v > 0 ? v : fallbackProductionWindow; }
    private int safetyDays() { int v = configService.safetyDays(); return v > 0 ? v : fallbackSafetyDays; }
    private int defaultLeadDays() { int v = configService.defaultLeadDays(); return v > 0 ? v : fallbackDefaultLead; }

    /**
     * 某台设备最近窗口内的日均运行强度。
     *
     * <p>三个值各自独立；源表没给或全是 0 的就是 null。设计成"各自可空"是因为
     * 现场就是这样——有的设备只报件数，有的只报时长。</p>
     */
    public record DeviceOutput(BigDecimal hoursPerDay, BigDecimal tasksPerDay, BigDecimal qtyPerDay) {
        static final DeviceOutput EMPTY = new DeviceOutput(null, null, null);
    }

    /** 全部在用备件的预测（含无法预测的，附带缺口说明） */
    public List<SparePartForecastVO> forecast() {
        LocalDate today = LocalDate.now();
        Map<String, DeviceOutput> outputs = averageDailyDevice(today);
        Map<Long, Integer> stockByProduct = availableStock();
        Map<Long, List<EquipmentPart>> linksByProduct = new HashMap<>();
        for (EquipmentPart link : partRepo.findAll()) {
            linksByProduct.computeIfAbsent(link.getProduct().getId(), k -> new ArrayList<>()).add(link);
        }

        List<SparePartForecastVO> rows = new ArrayList<>();
        for (Product product : productRepo.findAll()) {
            if (!product.isEnabled()) continue;
            rows.add(build(product, linksByProduct.getOrDefault(product.getId(), List.of()),
                    outputs, stockByProduct.getOrDefault(product.getId(), 0), today,
                    safetyDays(), productionWindowDays(), defaultLeadDays()));
        }
        // 需要行动的排前面，再按剩余天数升序
        rows.sort(Comparator
                .comparing((SparePartForecastVO v) -> severityRank(v.severity()))
                .thenComparing(v -> v.coverageDays() == null ? Integer.MAX_VALUE : v.coverageDays()));
        return rows;
    }

    /** 只有真要行动的才进预警中心，避免"每条都提醒"等于没有提醒 */
    public List<SparePartForecastVO> alerts() {
        return forecast().stream()
                .filter(v -> v.severity() != null)
                .toList();
    }

    private static int severityRank(String severity) {
        if ("HIGH".equals(severity)) return 0;
        if ("MEDIUM".equals(severity)) return 1;
        return 2;
    }

    /* ---------------- 取数 ---------------- */

    /** 每台设备最近窗口内的日均运行强度；窗口内没有任何一天数据的设备不进 map */
    private Map<String, DeviceOutput> averageDailyDevice(LocalDate today) {
        LocalDate from = today.minusDays(productionWindowDays());
        Map<String, Accumulator> acc = new LinkedHashMap<>();
        for (DeviceStatDaily row : statRepo.findByStatDateBetween(from, today)) {
            Accumulator slot = acc.computeIfAbsent(row.getDeviceCode(), k -> new Accumulator());
            slot.days += 1;
            slot.hours = slot.hours.add(nz(row.getRunningHours()));
            slot.tasks = slot.tasks.add(nz(row.getTaskCount()));
            slot.qty = slot.qty.add(nz(row.getTotalQty()));
        }
        Map<String, DeviceOutput> result = new HashMap<>();
        acc.forEach((code, slot) -> {
            if (slot.days == 0) return;
            BigDecimal days = BigDecimal.valueOf(slot.days);
            result.put(code, new DeviceOutput(
                    avg(slot.hours, days), avg(slot.tasks, days), avg(slot.qty, days)));
        });
        return result;
    }

    /** 可用库存 = 实存 − 已占用 */
    private Map<Long, Integer> availableStock() {
        Map<Long, Integer> stock = new HashMap<>();
        for (Inventory inventory : inventoryRepo.findAllWithGraph()) {
            stock.merge(inventory.getProduct().getId(), inventory.getAvailableQty(), Integer::sum);
        }
        return stock;
    }

    private static final class Accumulator {
        int days;
        BigDecimal hours = BigDecimal.ZERO;
        BigDecimal tasks = BigDecimal.ZERO;
        BigDecimal qty = BigDecimal.ZERO;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal nz(Long value) {
        return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value);
    }

    /** 平均值；窗口内全是 0 视为"这台设备不报这个口径" */
    private static BigDecimal avg(BigDecimal sum, BigDecimal days) {
        if (days.signum() <= 0) return null;
        BigDecimal value = sum.divide(days, 2, RoundingMode.HALF_UP);
        return value.signum() > 0 ? value : null;
    }

    /* ---------------- 计算 ---------------- */

    /**
     * 组装一个备件的预测。
     *
     * <p>抽成 static 是为了可以脱离数据库直接测算法——这个公式一旦算错，
     * 后果是现场备件备不够，比任何展示问题都严重。</p>
     */
    static SparePartForecastVO build(Product product,
                                     List<EquipmentPart> links,
                                     Map<String, DeviceOutput> outputs,
                                     int stockQty,
                                     LocalDate today,
                                     int safetyDays,
                                     int windowDays,
                                     int defaultLeadDays) {
        List<String> gaps = new ArrayList<>();
        List<SparePartForecastVO.DeviceDemand> devices = new ArrayList<>();
        List<LifeMetricRule> rules = LifeMetrics.parse(product.getLifeMetricRules(),
                product.getLifeMetric(), product.getRatedLifeValue());
        BigDecimal ratedHours = ratedOf(rules, "HOURS");
        BigDecimal ratedTasks = ratedOf(rules, "TASKS");
        BigDecimal ratedQty = ratedOf(rules, "QTY");
        BigDecimal ratedDays = ratedOf(rules, "DAYS");

        if (links.isEmpty()) {
            return unready(product, "NO_DEVICE", "未关联设备", stockQty,
                    List.of("该备件在基础数据里没关联到任何设备，无法从设备运行推算需求",
                            "去「基础数据 → 设备档案 → 关联设备备件」把该备件挂到设备上"));
        }

        // 配了额定值、但没有任何关联设备报得上数据的口径（HOURS/TASKS/QTY）
        Set<String> starving = new LinkedHashSet<>();
        if (ratedHours != null) starving.add("HOURS");
        if (ratedTasks != null) starving.add("TASKS");
        if (ratedQty != null) starving.add("QTY");

        BigDecimal total = BigDecimal.ZERO;
        boolean anyLife = false;
        for (EquipmentPart link : links) {
            Equipment equipment = link.getEquipment();
            if (equipment == null) continue;
            DeviceOutput out = outputs.getOrDefault(equipment.getCode(), DeviceOutput.EMPTY);
            if (out.hoursPerDay() != null) starving.remove("HOURS");
            if (out.tasksPerDay() != null) starving.remove("TASKS");
            if (out.qtyPerDay() != null) starving.remove("QTY");

            BigDecimal byHours = rateDemand(link.getInstalledQty(), out.hoursPerDay(), ratedHours);
            BigDecimal byTasks = rateDemand(link.getInstalledQty(), out.tasksPerDay(), ratedTasks);
            BigDecimal byQty = rateDemand(link.getInstalledQty(), out.qtyPerDay(), ratedQty);
            BigDecimal byCycle = cycleDemand(link.getInstalledQty(), link.getChangeCycleDays());
            BigDecimal byDays = daysDemand(link.getInstalledQty(), ratedDays);
            BigDecimal daily = daily(link.getInstalledQty(), out, ratedHours, ratedTasks, ratedQty,
                    link.getChangeCycleDays(), ratedDays);
            if (daily.signum() > 0) {
                anyLife = true;
                total = total.add(daily);
            }
            devices.add(new SparePartForecastVO.DeviceDemand(
                    equipment.getId(), equipment.getCode(), equipment.getName(), link.getInstalledQty(),
                    out.hoursPerDay(), out.tasksPerDay(), out.qtyPerDay(),
                    daily.setScale(2, RoundingMode.HALF_UP),
                    basisOf(daily, out, byHours, byTasks, byQty, byCycle, byDays,
                            ratedHours, ratedTasks, ratedQty, link.getChangeCycleDays(), ratedDays)));
        }

        if (!anyLife) {
            if (!starving.isEmpty()) {
                // 配了运行类口径，但关联设备一个都报不出对应数据：不是缺参数，是设备没在动/没上报
                return unready(product, "NO_PRODUCTION", "设备未上报", stockQty,
                        List.of("该备件按 " + String.join("、", starving) + " 计寿命，但关联设备最近 "
                                        + windowDays + " 天没有对应的运行数据，暂无可推算的消耗",
                                "确认设备是否停机或该字段未同步；或给设备关联填一个更换周期"));
            }
            return unready(product, "MISSING_LIFE", "参数不足", stockQty,
                    List.of("给该备件填寿命（口径 + 额定值），或给设备关联填更换周期"));
        }

        int rawLead = resolveLeadDays(product);
        boolean leadEstimated = rawLead <= 0;
        int effectiveLead = leadEstimated ? Math.max(defaultLeadDays, 0) : rawLead;
        int effectiveSafety = Math.max(safetyDays, 0);
        int safetyStock = resolveSafetyStock(product);

        Integer coverage = coverageDays(stockQty, total);
        LocalDate stockoutDate = coverage == null ? null : today.plusDays(coverage);
        // 最迟到货日：比见底日再提前安全天数，货掐着见底那天到等于没到
        LocalDate receiveByDate = stockoutDate == null ? null : stockoutDate.minusDays(effectiveSafety);
        LocalDate orderByDate = receiveByDate == null ? null : receiveByDate.minusDays(effectiveLead);
        Integer suggested = suggestQty(total, stockQty, safetyStock, rawLead, safetyDays, defaultLeadDays);

        if (leadEstimated) {
            gaps.add("未填采购周期，最晚采购日按 " + defaultLeadDays + " 天交期估算，实际请以供应商交期为准");
        }
        String severity = severityOf(today, coverage, stockoutDate, orderByDate, rawLead, safetyDays, defaultLeadDays);
        String hint = actionHint(today, coverage, receiveByDate, orderByDate, suggested,
                leadEstimated, defaultLeadDays, product.getUnit());

        return new SparePartForecastVO(product.getId(), product.getSku(), product.getName(), product.getUnit(),
                "READY", leadEstimated ? "无采购周期" : "可预测",
                total.setScale(2, RoundingMode.HALF_UP), stockQty, coverage, stockoutDate,
                receiveByDate, orderByDate, leadEstimated,
                leadEstimated ? null : rawLead, safetyStock, suggested, hint, severity, devices, gaps);
    }

    /**
     * 单机日耗：五个口径取最大。
     *
     * @param installedQty 这台设备上装了几个
     * @param output       这台设备最近日均运行强度，三个字段各自可空
     * @return 日耗；所有口径都给不出时为 0
     */
    static BigDecimal daily(int installedQty, DeviceOutput output,
                            BigDecimal ratedHours, BigDecimal ratedTasks, BigDecimal ratedQty,
                            Integer cycleDays, BigDecimal ratedDays) {
        return rateDemand(installedQty, output.hoursPerDay(), ratedHours)
                .max(rateDemand(installedQty, output.tasksPerDay(), ratedTasks))
                .max(rateDemand(installedQty, output.qtyPerDay(), ratedQty))
                .max(cycleDemand(installedQty, cycleDays))
                .max(daysDemand(installedQty, ratedDays));
    }

    /** 运行类口径：单机数量 × 设备日均强度 ÷ 额定寿命值 */
    static BigDecimal rateDemand(int installedQty, BigDecimal perDay, BigDecimal rated) {
        if (perDay == null || perDay.signum() <= 0 || rated == null || rated.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return perDay.multiply(BigDecimal.valueOf(Math.max(installedQty, 0)))
                .divide(rated, 6, RoundingMode.HALF_UP);
    }

    /** 件数口径：设备日产出 × 装机数 ÷ 额定可处理件数 */
    static BigDecimal qtyDemand(int installedQty, BigDecimal production, BigDecimal ratedQty) {
        return rateDemand(installedQty, production, ratedQty);
    }

    /** 运行中就会磨，但需要设备真在跑；按天数换一次：装机数 ÷ 天数 */
    static BigDecimal cycleDemand(int installedQty, Integer days) {
        if (days == null || days <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(Math.max(installedQty, 0))
                .divide(BigDecimal.valueOf(days), 6, RoundingMode.HALF_UP);
    }

    /** 天数口径的额定值可能是小数，不能走 intValue 截断 */
    static BigDecimal daysDemand(int installedQty, BigDecimal ratedDays) {
        if (ratedDays == null || ratedDays.signum() <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(Math.max(installedQty, 0))
                .divide(ratedDays, 6, RoundingMode.HALF_UP);
    }

    /** 库存除以日耗；日耗为 0 时无意义，返回 null */
    static Integer coverageDays(int stockQty, BigDecimal dailyDemand) {
        if (dailyDemand == null || dailyDemand.signum() <= 0) return null;
        if (stockQty <= 0) return 0;
        return BigDecimal.valueOf(stockQty)
                .divide(dailyDemand, 0, RoundingMode.DOWN)
                .intValue();
    }

    /**
     * 建议补货量：补到「撑过采购周期 + 安全天数」，再扣掉现有库存。
     *
     * <p>没填采购周期时按 {@code defaultLeadDays} 估算——缺这个参数不等于不用补货，
     * 库存已经撑不过一周还返回 0，会被读成「暂不用补」，这是会出事的那种错。</p>
     */
    static Integer suggestQty(BigDecimal dailyDemand, int stockQty, int safetyStock,
                              int leadDays, int safetyDays, int defaultLeadDays) {
        if (dailyDemand == null || dailyDemand.signum() <= 0) return null;
        int lead = leadDays > 0 ? leadDays : Math.max(defaultLeadDays, 0);
        int horizon = lead + Math.max(safetyDays, 0);
        BigDecimal target = dailyDemand.multiply(BigDecimal.valueOf(horizon))
                .add(BigDecimal.valueOf(Math.max(safetyStock, 0)));
        BigDecimal need = target.subtract(BigDecimal.valueOf(stockQty));
        if (need.signum() <= 0) return 0;
        return need.setScale(0, RoundingMode.CEILING).intValue();
    }

    /**
     * 严重度：
     * 已过最迟到货日或已断货 → HIGH；覆盖天数撑不过「采购周期 + 安全天数」→ MEDIUM；否则不告警。
     *
     * <p>注意这里<b>不因缺采购周期而降级</b>：没有交期数据不代表风险更低，
     * 恰恰相反，交期未知时更应该早点动。</p>
     */
    static String severityOf(LocalDate today, Integer coverage, LocalDate stockoutDate,
                             LocalDate orderByDate, int leadDays, int safetyDays, int defaultLeadDays) {
        if (coverage == null) return null;
        if (stockoutDate != null && !stockoutDate.isAfter(today)) return "HIGH";
        if (orderByDate != null && !orderByDate.isAfter(today)) return "HIGH";
        int lead = leadDays > 0 ? leadDays : Math.max(defaultLeadDays, 0);
        int horizon = lead + Math.max(safetyDays, 0);
        if (coverage <= horizon) return "MEDIUM";
        return null;
    }

    /**
     * 一句话行动建议，直接给采购看。
     *
     * <p>填了采购周期给「最晚采购日 + 最迟到货日 + 最少数量」；没填也照样给结论，
     * 只是把「交期未知」这件事明说出来，而不是不提醒。</p>
     */
    static String actionHint(LocalDate today, Integer coverage, LocalDate receiveByDate,
                             LocalDate orderByDate, Integer suggestedQty,
                             boolean leadEstimated, int defaultLeadDays, String unit) {
        if (coverage == null) return null;
        String u = StringUtils.hasText(unit) ? unit : "件";
        if (suggestedQty == null || suggestedQty <= 0) {
            return "当前库存可覆盖 " + coverage + " 天，暂不需补货";
        }
        String lot = suggestedQty + " " + u;
        if (coverage <= 0) {
            return "库存已见底，需立即到货 " + lot;
        }
        long late = receiveByDate == null ? 0 : today.toEpochDay() - receiveByDate.toEpochDay();
        if (late >= 0) {
            return "最迟到货日已过" + (late > 0 ? " " + late + " 天" : "") + "，需立即到货 " + lot;
        }
        if (leadEstimated) {
            return "需在 " + receiveByDate + " 前到货 " + lot
                    + "；未填采购周期，交期按 " + defaultLeadDays + " 天估算，建议以实际交期倒推下单";
        }
        return "需在 " + orderByDate + " 前完成采购，" + receiveByDate + " 前到货，最少 " + lot;
    }

    private static BigDecimal ratedOf(List<LifeMetricRule> rules, String metric) {
        for (LifeMetricRule rule : rules) {
            if (metric.equals(rule.metric()) && rule.rated() != null && rule.rated().signum() > 0) {
                return rule.rated();
            }
        }
        return null;
    }

    /** 采购周期只看备件档案：一个 SKU 只有一条供应渠道，装在哪台设备都一样 */
    private static int resolveLeadDays(Product product) {
        return Math.max(product.getProcurementLeadDays(), 0);
    }

    /** 安全库存只用备件档案里填的值；没填就是 0，不拿日耗替你编一个数 */
    private static int resolveSafetyStock(Product product) {
        return Math.max(product.getSafetyStock(), 0);
    }

    /**
     * 只描述<b>真正决定结果</b>的那个口径。把所有口径都写上去会误导现场——
     * 例如某备件既填了「10 万件」又填了「30 天」，实际生效的是 30 天那个，
     * 把 10 万件也列出来会让人以为产量已经参与计算了。
     */
    private static String basisOf(BigDecimal daily, DeviceOutput out,
                                  BigDecimal byHours, BigDecimal byTasks, BigDecimal byQty,
                                  BigDecimal byCycle, BigDecimal byDays,
                                  BigDecimal ratedHours, BigDecimal ratedTasks, BigDecimal ratedQty,
                                  Integer cycleDays, BigDecimal ratedDays) {
        if (daily == null || daily.signum() <= 0) return "缺参数：没有寿命额定值也没有更换周期";
        List<String> parts = new ArrayList<>();
        if (byHours.signum() > 0 && byHours.compareTo(daily) == 0) {
            parts.add("日均运行 " + plain(out.hoursPerDay()) + " 小时 ÷ 额定 " + plain(ratedHours) + " 小时");
        }
        if (byTasks.signum() > 0 && byTasks.compareTo(daily) == 0) {
            parts.add("日均 " + plain(out.tasksPerDay()) + " 次任务 ÷ 额定 " + plain(ratedTasks) + " 次");
        }
        if (byQty.signum() > 0 && byQty.compareTo(daily) == 0) {
            parts.add("日均产出 " + plain(out.qtyPerDay()) + " 件 ÷ 寿命 " + plain(ratedQty) + " 件");
        }
        if (byCycle.signum() > 0 && byCycle.compareTo(daily) == 0) {
            parts.add("更换周期 " + cycleDays + " 天");
        }
        if (byDays.signum() > 0 && byDays.compareTo(daily) == 0) {
            parts.add("寿命 " + plain(ratedDays) + " 天");
        }
        String text = "按" + String.join(" 与 ", parts) + " 计 " + plain(daily.setScale(2, RoundingMode.HALF_UP)) + " 件/天";
        // 设备明明有数据，却没配对应口径，数据就被浪费了——这句提示能让人知道补什么参数
        List<String> wasted = new ArrayList<>();
        if (byHours.signum() <= 0 && out.hoursPerDay() != null) wasted.add("运行时长");
        if (byTasks.signum() <= 0 && out.tasksPerDay() != null) wasted.add("任务次数");
        if (byQty.signum() <= 0 && out.qtyPerDay() != null) wasted.add("处理件数");
        if (!wasted.isEmpty()) {
            text += "（设备有" + String.join("、", wasted) + "数据，但未配对应口径，未参与计算）";
        }
        return text;
    }

    private static String plain(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    private static SparePartForecastVO unready(Product product, String status, String statusText,
                                               int stockQty, List<String> gaps) {
        return new SparePartForecastVO(product.getId(), product.getSku(), product.getName(), product.getUnit(),
                status, statusText, null, stockQty, null, null, null, null, false,
                product.getProcurementLeadDays() > 0 ? product.getProcurementLeadDays() : null,
                Math.max(product.getSafetyStock(), 0), null, null, null,
                List.of(), new ArrayList<>(gaps));
    }
}
