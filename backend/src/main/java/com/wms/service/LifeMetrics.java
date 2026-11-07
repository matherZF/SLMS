package com.wms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wms.dto.LifeMetricRule;
import com.wms.dto.LifeMetricUsageVO;
import com.wms.entity.SparePartUsage;
import com.wms.entity.SparePartUsageSnapshot;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 寿命计量口径的统一计算入口。
 *
 * <p>一个备件可以配置多条计量规则（例如"运行 8000 小时"+"启停 20 万次"+"处理 5 万件"），
 * 每条规则有各自的额定值，单位互不相同。已用比例按各自口径分别计算，
 * 取百分比最高的一条作为<b>主导口径</b>——即"谁先到保养谁"，与汽车里程/时间双保逻辑一致。</p>
 *
 * <p>历史的单口径配置（life_metric + rated_life_value）在解析时自动折算成一条规则，行为不变。</p>
 */
public final class LifeMetrics {

    /** 支持的口径；NONE 表示不计算 */
    public static final Set<String> SUPPORTED = Set.of("HOURS", "TASKS", "QTY", "DAYS");
    /** 需要设备侧累计数据的口径 */
    private static final Set<String> NEEDS_DEVICE = Set.of("HOURS", "TASKS", "QTY");
    /** 需要设备运行实时视图（累计时长/次数）的口径 */
    private static final Set<String> NEEDS_RUNTIME_VIEW = Set.of("HOURS", "TASKS");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LifeMetrics() {
    }

    /** 规范化口径标识；不合法或 NONE 返回 null */
    public static String canonical(String metric) {
        if (!StringUtils.hasText(metric)) return null;
        String m = metric.trim().toUpperCase();
        return SUPPORTED.contains(m) ? m : null;
    }

    /** 单位文字，用于提示文案与前端展示 */
    public static String unit(String metric) {
        return switch (metric == null ? "" : metric) {
            case "HOURS" -> "小时";
            case "TASKS" -> "次";
            case "QTY" -> "件";
            case "DAYS" -> "天";
            default -> "";
        };
    }

    /**
     * 解析规则：优先用 JSON，为空/损坏时回落到旧字段。
     * 会去重（同口径只保留第一条）、丢弃非法或额定值非正数的规则。
     */
    public static List<LifeMetricRule> parse(String json, String legacyMetric, BigDecimal legacyRated) {
        List<LifeMetricRule> rules = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (StringUtils.hasText(json)) {
            try {
                JsonNode root = MAPPER.readTree(json);
                if (root != null && root.isArray()) {
                    for (JsonNode node : root) {
                        String metric = canonical(node.path("metric").asText(null));
                        if (metric == null || !seen.add(metric)) continue;
                        BigDecimal rated = node.hasNonNull("rated") ? node.get("rated").decimalValue() : null;
                        if (rated == null || rated.signum() <= 0) {
                            seen.remove(metric);
                            continue;
                        }
                        rules.add(new LifeMetricRule(metric, rated));
                    }
                }
            } catch (Exception ignored) {
                // JSON 损坏按无规则处理，下面回落到旧字段
            }
        }
        if (rules.isEmpty()) {
            String metric = canonical(legacyMetric);
            if (metric != null && legacyRated != null && legacyRated.signum() > 0) {
                rules.add(new LifeMetricRule(metric, legacyRated));
            }
        }
        return rules;
    }

    public static String toJson(List<LifeMetricRule> rules) {
        if (rules == null || rules.isEmpty()) return null;
        ArrayNode arr = MAPPER.createArrayNode();
        for (LifeMetricRule rule : rules) {
            ObjectNode node = arr.addObject();
            node.put("metric", rule.metric());
            node.put("rated", rule.rated());
        }
        return arr.toString();
    }

    /** 校验并规范化待保存的规则，返回可直接入库的 JSON；无有效规则返回 null */
    public static String validateAndSerialize(List<LifeMetricRule> rules) {
        if (rules == null || rules.isEmpty()) return null;
        List<LifeMetricRule> clean = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (LifeMetricRule rule : rules) {
            if (rule == null) continue;
            String metric = canonical(rule.metric());
            if (metric == null) throw new IllegalArgumentException("寿命计量方式无效：" + rule.metric());
            if (!seen.add(metric)) continue;
            if (rule.rated() == null || rule.rated().signum() <= 0) {
                throw new IllegalArgumentException("额定寿命必须大于 0（" + metric + "）");
            }
            clean.add(new LifeMetricRule(metric, rule.rated()));
        }
        return toJson(clean);
    }

    /** 是否涉及设备侧累计数据（决定刷新时要不要连设备库） */
    public static boolean needsDeviceData(List<LifeMetricRule> rules) {
        return rules != null && rules.stream().anyMatch(r -> NEEDS_DEVICE.contains(r.metric()));
    }

    /** 是否需要设备运行实时视图（累计时长/次数） */
    public static boolean needsRuntimeView(List<LifeMetricRule> rules) {
        return rules != null && rules.stream().anyMatch(r -> NEEDS_RUNTIME_VIEW.contains(r.metric()));
    }

    public static boolean needsPieceQty(List<LifeMetricRule> rules) {
        return rules != null && rules.stream().anyMatch(r -> "QTY".equals(r.metric()));
    }

    /** 某口径下的已用量；数据不足返回 null */
    public static BigDecimal used(SparePartUsage usage, String metric) {
        if (usage == null || metric == null) return null;
        return switch (metric) {
            case "HOURS" -> usage.getBaselineRunningHours() == null || usage.getCurrentRunningHours() == null
                    ? null
                    : usage.getCurrentRunningHours().subtract(usage.getBaselineRunningHours()).max(BigDecimal.ZERO);
            case "TASKS" -> usage.getBaselineTaskCount() == null || usage.getCurrentTaskCount() == null
                    ? null
                    : BigDecimal.valueOf(Math.max(0L, usage.getCurrentTaskCount() - usage.getBaselineTaskCount()));
            case "QTY" -> usage.getBaselinePieceQty() == null || usage.getCurrentPieceQty() == null
                    ? null
                    : BigDecimal.valueOf(Math.max(0L, usage.getCurrentPieceQty() - usage.getBaselinePieceQty()));
            case "DAYS" -> usage.getStartedAt() == null
                    ? null
                    : BigDecimal.valueOf(Math.max(0L, Duration.between(usage.getStartedAt(), Instant.now()).toDays()));
            default -> null;
        };
    }

    public static Integer percent(BigDecimal used, BigDecimal rated) {
        if (used == null || rated == null || rated.signum() <= 0) return null;
        return used.multiply(BigDecimal.valueOf(100)).divide(rated, 0, RoundingMode.HALF_UP).intValue();
    }

    /** 逐口径计算已用/剩余/百分比，并把百分比最高的一条标记为 primary */
    public static List<LifeMetricUsageVO> evaluate(SparePartUsage usage, List<LifeMetricRule> rules) {
        return evaluate(usage, rules, null, Instant.now());
    }

    /**
     * 带预测的版本：额外给出各口径的日均消耗与寿命耗尽日。
     *
     * @param rates 各口径的消耗速率，null 或缺少某口径时不预测该口径
     */
    public static List<LifeMetricUsageVO> evaluate(SparePartUsage usage, List<LifeMetricRule> rules,
                                                   Map<String, Rate> rates, Instant now) {
        if (rules == null || rules.isEmpty()) return List.of();
        Instant at = now == null ? Instant.now() : now;
        List<LifeMetricUsageVO> rows = new ArrayList<>(rules.size());
        for (LifeMetricRule rule : rules) {
            BigDecimal used = used(usage, rule.metric());
            BigDecimal remaining = used == null ? null : rule.rated().subtract(used).max(BigDecimal.ZERO);
            Rate rate = rates == null ? null : rates.get(rule.metric());
            BigDecimal perDay = rate == null ? null : rate.perDay();
            Integer sampleDays = rate == null ? null : rate.sampleDays();
            rows.add(new LifeMetricUsageVO(rule.metric(), rule.rated(), used, remaining,
                    percent(used, rule.rated()), false, perDay, sampleDays,
                    endAt(rule.metric(), usage, remaining, rule.rated(), perDay, at)));
        }
        int dominant = 0;
        int best = Integer.MIN_VALUE;
        for (int i = 0; i < rows.size(); i++) {
            Integer pct = rows.get(i).usedPercent();
            if (pct != null && pct > best) {
                best = pct;
                dominant = i;
            }
        }
        rows.set(dominant, rows.get(dominant).withPrimary(true));
        return rows;
    }

    /**
     * 单口径的寿命耗尽日。
     * <p>DAYS 口径不需要速率：开始使用时间加上额定天数就是到期日。
     * 其余口径用「剩余量 ÷ 日均消耗」外推；设备闲置（速率为 0）或样本不足时不猜。</p>
     */
    private static Instant endAt(String metric, SparePartUsage usage, BigDecimal remaining,
                                 BigDecimal rated, BigDecimal perDay, Instant now) {
        if ("DAYS".equals(metric)) {
            return usage == null || usage.getStartedAt() == null
                    ? null : usage.getStartedAt().plus(Duration.ofDays(rated.longValue()));
        }
        if (remaining == null || perDay == null || perDay.signum() <= 0) return null;
        if (remaining.signum() <= 0) return now;
        long days = remaining.divide(perDay, 0, RoundingMode.CEILING).longValue();
        return now.plus(Duration.ofDays(days));
    }

    /** 某口径的消耗速率：单位/天；sampleDays 为拟合所用样本的跨度 */
    public record Rate(BigDecimal perDay, int sampleDays) {
    }

    /** 两个候选更换日取早，null 表示该来源没有结论 */
    public static Instant earliest(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }

    /**
     * 从快照序列拟合各口径的日均消耗。
     *
     * <p>用首尾两个有效累计值做差除以天数跨度，而不是线性回归：累计值是单调递增的，
     * 首尾做差已经等价于总增量除以总时长，对缺失的中间点不敏感。</p>
     *
     * <p>跨度不足 {@code minSampleDays} 天时只返回跨度、不返回速率——短窗口里的
     * 波动（比如一次停机）会把日均算歪，宁可不预测。</p>
     */
    public static Map<String, Rate> rates(List<SparePartUsageSnapshot> snapshots, List<LifeMetricRule> rules,
                                          int minSampleDays) {
        Map<String, Rate> out = new LinkedHashMap<>();
        if (snapshots == null || snapshots.isEmpty() || rules == null || rules.isEmpty()) return out;
        List<SparePartUsageSnapshot> ordered = new ArrayList<>(snapshots);
        ordered.sort(Comparator.comparing(SparePartUsageSnapshot::getCollectedAt));
        int minDays = Math.max(1, minSampleDays);
        for (LifeMetricRule rule : rules) {
            String metric = rule.metric();
            if (!NEEDS_DEVICE.contains(metric)) continue;
            SparePartUsageSnapshot first = null;
            SparePartUsageSnapshot last = null;
            BigDecimal firstValue = null;
            BigDecimal lastValue = null;
            for (SparePartUsageSnapshot snapshot : ordered) {
                BigDecimal value = cumulative(snapshot, metric);
                if (value == null) continue;
                if (first == null) {
                    first = snapshot;
                    firstValue = value;
                }
                last = snapshot;
                lastValue = value;
            }
            if (first == null || first == last) continue;
            long minutes = Duration.between(first.getCollectedAt(), last.getCollectedAt()).toMinutes();
            if (minutes <= 0) continue;
            int days = (int) Math.max(1, Math.ceil(minutes / 1440.0));
            BigDecimal delta = lastValue.subtract(firstValue);
            BigDecimal perDay = null;
            if (days >= minDays && delta.signum() > 0) {
                perDay = delta.multiply(BigDecimal.valueOf(1440))
                        .divide(BigDecimal.valueOf(minutes), 4, RoundingMode.HALF_UP);
            }
            out.put(metric, new Rate(perDay, days));
        }
        return out;
    }

    /** 快照里某口径的累计值 */
    private static BigDecimal cumulative(SparePartUsageSnapshot snapshot, String metric) {
        return switch (metric) {
            case "HOURS" -> snapshot.getRunningHours();
            case "TASKS" -> snapshot.getTaskCount() == null ? null : BigDecimal.valueOf(snapshot.getTaskCount());
            case "QTY" -> snapshot.getPieceQty() == null ? null : BigDecimal.valueOf(snapshot.getPieceQty());
            default -> null;
        };
    }

    /** 主导口径（百分比最高的一条），无规则返回 null */
    public static LifeMetricUsageVO dominant(List<LifeMetricUsageVO> rows) {
        if (rows == null || rows.isEmpty()) return null;
        for (LifeMetricUsageVO row : rows) {
            if (row.primary()) return row;
        }
        return rows.get(0);
    }

    /**
     * 最早到期的口径——决定「什么时候该换」的那条。
     *
     * <p>与 {@link #dominant} 不是一回事：主导口径按已用比例选（反映磨损程度），
     * 而更换日期要按谁先耗尽选。天数口径常常比例很低但日期最先到，
     * 用主导口径的日期会得出一个明显偏晚的更换日。</p>
     */
    public static LifeMetricUsageVO soonest(List<LifeMetricUsageVO> rows) {
        if (rows == null || rows.isEmpty()) return null;
        return rows.stream()
                .filter(row -> row.expectedEndAt() != null)
                .min(Comparator.comparing(LifeMetricUsageVO::expectedEndAt))
                .orElse(null);
    }
}
