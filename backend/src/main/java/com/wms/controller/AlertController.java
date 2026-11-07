package com.wms.controller;

import com.wms.common.Result;
import com.wms.service.AlertService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {
    private final AlertService s;

    public AlertController(AlertService s) { this.s = s; }

    /** 预警需要 page:alerts；读的是预警表，不是每次实时算 */
    @GetMapping
    @PreAuthorize("hasAuthority('page:alerts')")
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String status) {
        return Result.ok(s.inbox(status));
    }

    /** 立即重算并落表：定时任务之外，现场改完参数想马上看结果时用 */
    @PostMapping("/refresh")
    @PreAuthorize("hasAuthority('page:alerts')")
    public Result<Integer> refresh() { return Result.ok(s.snapshot()); }

    /** 实时算、不落表：用于核对"现在到底什么情况" */
    @GetMapping("/live")
    @PreAuthorize("hasAuthority('page:alerts')")
    public Result<List<Map<String, Object>>> live() { return Result.ok(s.alerts()); }

    /** 站内消息未读数 */
    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public Result<Long> unreadCount() { return Result.ok(s.unreadCount()); }

    @PostMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> read(@PathVariable Long id) { s.markRead(id); return Result.ok(null); }

    @PostMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> readAll() { s.markAllRead(); return Result.ok(null); }

    @PostMapping("/{id}/ack")
    @PreAuthorize("hasAuthority('page:alerts')")
    public Result<Void> ack(@PathVariable Long id) { s.acknowledge(id); return Result.ok(null); }

    @PostMapping("/{id}/ignore")
    @PreAuthorize("hasAuthority('page:alerts')")
    public Result<Void> ignore(@PathVariable Long id) { s.ignore(id); return Result.ok(null); }
}
