package com.wms.controller;

import com.wms.common.Result;
import com.wms.dto.NotificationItem;
import com.wms.dto.NotificationReadRequest;
import com.wms.service.NotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 站内消息中心：聚合预警/待审批，已读只影响红点、不影响消息是否展示 */
@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /** 拉取消息条目（未读在前，已读的继续保留） */
    @GetMapping
    public Result<List<NotificationItem>> list() {
        return Result.ok(service.list(username()));
    }

    /** 铃铛红点：未读条数 */
    @GetMapping("/count")
    public Result<Long> count() {
        return Result.ok(service.totalUnread(username()));
    }

    /** 单条标记已读：红点 -1，消息留在列表里 */
    @PostMapping("/read")
    public Result<Void> markRead(@RequestBody NotificationReadRequest req) {
        service.markRead(username(), req);
        return Result.ok();
    }

    /** 全部标为已读：红点清零，消息全部转为已读态 */
    @PostMapping("/read-all")
    public Result<Void> markAllRead() {
        service.markAllRead(username());
        return Result.ok();
    }

    private String username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }
}
