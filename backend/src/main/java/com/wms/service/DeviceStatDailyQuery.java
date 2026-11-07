package com.wms.service;

import com.wms.dto.DeviceStatDailyVO;
import com.wms.entity.DeviceStatDaily;
import com.wms.repository.DeviceStatDailyRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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
     * 跨源合计版：同一台设备（stat_date + device_type + device_code 相同）在 DT/WMS/WCS
     * 各存一行（本地表唯一键含 source_sys），RB 等设备的时长/任务数/件数分散在多个源里，
     * 展示时按日合并求和；sourceSys 拼成「DT+WMS」标明数据来自哪些源。按统计日倒序返回。
     */
    public List<DeviceStatDailyVO> listMerged(String deviceCode, LocalDate from, LocalDate to) {
        List<DeviceStatDaily> rows = repo.findByStatDateBetween(from, to);
        Map<String, DeviceStatDailyVO> merged = new LinkedHashMap<>();
        for (DeviceStatDaily r : rows) {
            if (deviceCode != null && !deviceCode.equalsIgnoreCase(r.getDeviceCode())) continue;
            String key = r.getStatDate() + "|" + r.getDeviceType() + "|" + r.getDeviceCode();
            DeviceStatDailyVO prev = merged.get(key);
            if (prev == null) {
                merged.put(key, toVO(r));
            } else {
                merged.put(key, new DeviceStatDailyVO(
                        prev.id(),
                        prev.statDate(),
                        mergeSource(prev.sourceSys(), r.getSourceSys()),
                        prev.deviceType(),
                        prev.deviceCode(),
                        nz(prev.runningHours()).add(nz(r.getRunningHours())),
                        prev.taskCount() + nz(r.getTaskCount()),
                        prev.totalQty() + nz(r.getTotalQty())));
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
