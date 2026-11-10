package com.wms.service;

import com.wms.entity.DeviceStatDaily;
import com.wms.repository.DeviceStatDailyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 设备累计运行量（件数 / 运行时长 / 任务次数）的取数入口。
 *
 * <p>数据来自定时同步落地的本地表 {@code wms_device_stat_daily}，累计口径完全按设备的
 * 映射规则（{@link DeviceCodeResolver.MappingRule}）来：哪一行勾了哪个指标，
 * 就只累加那个指标；同指标多行命中时合计。没配映射的设备取不到数。</p>
 *
 * <p><b>无数据一律返回 null，不能返回 0</b>：0 会被当成真实的累计值建立基准，
 * 之后要等很久才会动，属于静默错误。判断标准是"有没有真报过"——
 * 行存在但该列全是 0 或 NULL，说明源系统不提供这个字段（例如部分设备的
 * {@code SUM_HOURS} 恒为 0），同样按无数据处理，由调用方决定退回别的口径还是报缺口。</p>
 */
@Service
public class DeviceCumulativeService {

    private final DeviceStatDailyRepository repo;
    private final DeviceCodeResolver resolver;
    /** 全局兜底：件数只认某一个源（逐设备的更精细控制在映射行里） */
    private final String pieceSource;

    public DeviceCumulativeService(DeviceStatDailyRepository repo,
                                   DeviceCodeResolver resolver,
                                   @Value("${spare-part.usage-sync.piece-source:}") String pieceSource) {
        this.repo = repo;
        this.resolver = resolver;
        this.pieceSource = pieceSource;
    }

    private enum Metric { HOURS, TASKS, QTY }

    /** 设备累计处理件数；没有勾选件数的映射行、或从未上报过时返回 null */
    public Long cumulativeQty(String equipmentCode) {
        if (!StringUtils.hasText(equipmentCode)) return null;
        List<DeviceStatDaily> rows = rowsByRules(equipmentCode, Metric.QTY);
        if (rows.isEmpty()) return null;
        BigDecimal sum = BigDecimal.ZERO;
        for (DeviceStatDaily r : rows) {
            if (r.getTotalQty() != null) sum = sum.add(BigDecimal.valueOf(r.getTotalQty()));
        }
        return sum.longValue();
    }

    /**
     * 设备累计运行小时；该设备从未上报过运行时长（或规则里没勾时长）时返回 null。
     *
     * <p>不能因为源表这列全 0 就当作"设备没跑过"——立项素材里明确写了
     * {@code SUM_HOURS} 只有部分设备拿得到，全 0 更可能是字段没采。</p>
     */
    public BigDecimal cumulativeRunningHours(String equipmentCode) {
        if (!StringUtils.hasText(equipmentCode)) return null;
        List<DeviceStatDaily> rows = rowsByRules(equipmentCode, Metric.HOURS);
        boolean reported = false;
        BigDecimal sum = BigDecimal.ZERO;
        for (DeviceStatDaily r : rows) {
            BigDecimal h = r.getRunningHours();
            if (h != null && h.signum() > 0) reported = true;
            if (h != null) sum = sum.add(h);
        }
        return reported ? sum : null;
    }

    /** 设备累计任务次数；从未上报过（或规则里没勾任务数）时返回 null */
    public Long cumulativeTaskCount(String equipmentCode) {
        if (!StringUtils.hasText(equipmentCode)) return null;
        List<DeviceStatDaily> rows = rowsByRules(equipmentCode, Metric.TASKS);
        boolean reported = false;
        long sum = 0L;
        for (DeviceStatDaily r : rows) {
            long t = r.getTaskCount() == null ? 0L : r.getTaskCount();
            if (t > 0) reported = true;
            sum += t;
        }
        return reported ? sum : null;
    }

    /**
     * 取出符合"该指标"的映射规则命中的全部历史行。
     *
     * <p>命中判定是（源 + 编码）双条件：同一个编码在不同源里代表不同设备时，
     * 只看编码会把别的源的数据算进来。件数另有全局 piece-source 兜底：
     * 配了它时只有该源的件数计入，等价于把所有其它行的件数勾掉。</p>
     */
    private List<DeviceStatDaily> rowsByRules(String equipmentCode, Metric metric) {
        List<DeviceCodeResolver.MappingRule> rules = resolver.rules(equipmentCode.trim());
        if (rules.isEmpty()) return List.of();
        Set<String> keys = new HashSet<>();
        String globalPieceSource = StringUtils.hasText(pieceSource) ? pieceSource.trim().toUpperCase() : null;
        for (DeviceCodeResolver.MappingRule rule : rules) {
            boolean takes = switch (metric) {
                case HOURS -> rule.takeHours();
                case TASKS -> rule.takeTasks();
                case QTY -> rule.takeQty()
                        && (globalPieceSource == null || globalPieceSource.equalsIgnoreCase(rule.sourceSys()));
            };
            if (takes) keys.add(rule.matchKey());
        }
        if (keys.isEmpty()) return List.of();
        List<DeviceStatDaily> out = new ArrayList<>();
        for (DeviceStatDaily row : repo.findByDeviceCodeIn(resolver.codes(rules))) {
            if (keys.contains(DeviceCodeResolver.normalizeKey(row.getSourceSys(), row.getDeviceCode()))) {
                out.add(row);
            }
        }
        return out;
    }
}
