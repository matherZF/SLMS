package com.wms.controller;

import com.wms.common.Result;
import com.wms.dto.DeviceStatDailyVO;
import com.wms.service.DeviceStatDailyQuery;
import com.wms.service.DeviceStatDailySyncService;
import com.wms.service.OperationAuditService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 设备每日运行数据：查看 + 手动补拉。
 * 同步服务是条件装配的（ext-stat.enabled），未接入外部系统时用对象提供者拿不到实例，返回友好提示。
 */
@RestController
@RequestMapping("/api/device-stats")
@PreAuthorize("hasAuthority('system:interface')")
public class DeviceStatDailyController {
    private final DeviceStatDailyQuery query;
    private final ObjectProvider<DeviceStatDailySyncService> syncProvider;
    private final OperationAuditService audit;

    public DeviceStatDailyController(DeviceStatDailyQuery query,
                                     ObjectProvider<DeviceStatDailySyncService> syncProvider,
                                     OperationAuditService audit) {
        this.query = query;
        this.syncProvider = syncProvider;
        this.audit = audit;
    }

    @GetMapping
    public Result<List<DeviceStatDailyVO>> list(@RequestParam(required = false) String sourceSys,
                                                @RequestParam(required = false) String deviceCode,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(query.list(sourceSys, deviceCode, from, to));
    }

    /** 手动补拉：不传 date 默认拉昨天，重复拉取是覆盖更新，不会产生脏数据 */
    @PostMapping("/sync")
    public Result<Object> sync(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        DeviceStatDailySyncService service = syncProvider.getIfAvailable();
        if (service == null) return Result.fail("外部设备运行数据源未启用（ext-stat.enabled=false）");
        DeviceStatDailySyncService.SyncResult result = service.sync(date);
        // 审计记实际拉取的日期：不传 date 时是"昨天"，不能记成 null
        audit.record("DEVICE_STAT", 0L, result.statDate().toString(), "SYNC", null, null,
                "人工补拉设备运行数据；成功源：" + result.successSources() + "，失败源：" + result.failedSources()
                        + "，条数：" + result.rows());
        return Result.ok(result);
    }

    /**
     * 按日期区间补拉，用于首次接入时把最近几天的数据一次性灌进来。
     * 两种用法：① 传 from/to；② 都不传时用 days 拉「到昨天为止」的最近 N 天。
     */
    @PostMapping("/sync-range")
    public Result<Object> syncRange(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                    @RequestParam(required = false) Integer days) {
        DeviceStatDailySyncService service = syncProvider.getIfAvailable();
        if (service == null) return Result.fail("外部设备运行数据源未启用（ext-stat.enabled=false）");
        List<DeviceStatDailySyncService.SyncResult> results;
        try {
            results = service.syncRange(from, to, days);
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
        int rows = results.stream().mapToInt(DeviceStatDailySyncService.SyncResult::rows).sum();
        int failed = results.stream().mapToInt(DeviceStatDailySyncService.SyncResult::failedSources).sum();
        audit.record("DEVICE_STAT", 0L,
                results.isEmpty() ? "-" : results.get(0).statDate() + " ~ " + results.get(results.size() - 1).statDate(),
                "SYNC_RANGE", null, null,
                "区间补拉设备运行数据；天数：" + results.size() + "，失败源次数：" + failed + "，总条数：" + rows);
        return Result.ok(results);
    }
}
