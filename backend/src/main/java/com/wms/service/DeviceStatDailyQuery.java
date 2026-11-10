package com.wms.service;

import com.wms.dto.DeviceStatDailyVO;
import com.wms.entity.DeviceStatDaily;
import com.wms.repository.DeviceStatDailyRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** 本地表 wms_device_stat_daily 的查询入口（与是否接入外部系统无关，始终可用） */
@Service
public class DeviceStatDailyQuery {
    private final DeviceStatDailyRepository repo;

    public DeviceStatDailyQuery(DeviceStatDailyRepository repo) {
        this.repo = repo;
    }

    public List<DeviceStatDailyVO> list(String sourceSys, String deviceCode, LocalDate from, LocalDate to) {
        List<DeviceStatDaily> rows;
        if (from != null && to != null) {
            rows = repo.findByStatDateBetween(from, to);
        } else if (sourceSys != null && from != null) {
            rows = repo.findBySourceSysAndStatDateOrderByDeviceTypeAscDeviceCodeAsc(sourceSys, from);
        } else if (deviceCode != null) {
            rows = repo.findByDeviceCodeOrderByStatDateDesc(deviceCode);
        } else {
            rows = repo.findAll();
        }
        return rows.stream()
                .filter(r -> sourceSys == null || sourceSys.equalsIgnoreCase(r.getSourceSys()))
                .filter(r -> deviceCode == null || deviceCode.equalsIgnoreCase(r.getDeviceCode()))
                .map(this::toVO)
                .toList();
    }

    /**
     * 按设备的映射规则合并运行数据（设备详情页用）。
     *
     * <p>规则来自 {@link DeviceCodeResolver#rules(String)}——<b>没有默认</b>，没配映射就返回空。
     * 只取规则命中的（源 + 编码）行，并且只累加该行勾选的指标：没勾的按 0 处理，
     * 避免"两个源都供件数"时口径翻倍。同一天同一设备类型的多行（多编码/多源）合并成一行，
     * sourceSys 拼「DT+WMS」、deviceCode 拼「STA001+STA002」标明数据由哪些编码合计而来。
     * 按统计日倒序返回。</p>
     */
    public List<DeviceStatDailyVO> listMergedByRules(List<DeviceCodeResolver.MappingRule> rules,
                                                     LocalDate from, LocalDate to) {
        if (rules == null || rules.isEmpty()) return List.of();
        Map<String, DeviceCodeResolver.MappingRule> byKey = new HashMap<>();
        List<String> codes = new ArrayList<>();
        for (DeviceCodeResolver.MappingRule rule : rules) {
            byKey.put(rule.matchKey(), rule);
            if (!codes.contains(rule.extCode())) codes.add(rule.extCode());
        }
        String codeLabel = String.join("+", codes);
        List<DeviceStatDaily> rows = repo.findByStatDateBetweenAndDeviceCodeIn(from, to, codes);
        Map<String, DeviceStatDailyVO> merged = new LinkedHashMap<>();
        for (DeviceStatDaily r : rows) {
            DeviceCodeResolver.MappingRule rule = byKey.get(
                    DeviceCodeResolver.normalizeKey(r.getSourceSys(), r.getDeviceCode()));
            if (rule == null) continue;   // 编码对上了但源不在规则里：不取
            BigDecimal hours = rule.takeHours() ? nz(r.getRunningHours()) : BigDecimal.ZERO;
            long tasks = rule.takeTasks() ? nz(r.getTaskCount()) : 0L;
            long qty = rule.takeQty() ? nz(r.getTotalQty()) : 0L;
            String key = r.getStatDate() + "|" + r.getDeviceType();
            DeviceStatDailyVO prev = merged.get(key);
            if (prev == null) {
                merged.put(key, new DeviceStatDailyVO(r.getId(), r.getStatDate(), r.getSourceSys(),
                        r.getDeviceType(), codeLabel, hours, tasks, qty));
            } else {
                merged.put(key, new DeviceStatDailyVO(prev.id(), prev.statDate(),
                        mergeSource(prev.sourceSys(), r.getSourceSys()), prev.deviceType(), prev.deviceCode(),
                        nz(prev.runningHours()).add(hours), prev.taskCount() + tasks, prev.totalQty() + qty));
            }
        }
        List<DeviceStatDailyVO> out = new ArrayList<>(merged.values());
        out.sort((a, b) -> b.statDate().compareTo(a.statDate()));
        return out;
    }

    private static String mergeSource(String a, String b) {
        TreeSet<String> set = new TreeSet<>();
        if (a != null) for (String s : a.split("\\+")) set.add(s.trim());
        if (b != null) for (String s : b.split("\\+")) set.add(s.trim());
        return String.join("+", set);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private static long nz(Long v) { return v == null ? 0L : v; }

    private DeviceStatDailyVO toVO(DeviceStatDaily r) {
        return new DeviceStatDailyVO(r.getId(), r.getStatDate(), r.getSourceSys(), r.getDeviceType(),
                r.getDeviceCode(), r.getRunningHours(), r.getTaskCount(), r.getTotalQty());
    }
}
