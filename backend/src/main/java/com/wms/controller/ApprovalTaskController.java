package com.wms.controller;

import com.wms.common.Result;
import com.wms.dto.ApprovalTaskVO;
import com.wms.service.ApprovalService;
import com.wms.service.BusinessApprovalService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 审批中心。三个接口原先没有任何权限注解，只要登录就能列出并审批任意待办，
 * 这里按"页面 / 按钮"两级权限补齐。
 */
@RestController
@RequestMapping("/api/approval-tasks")
public class ApprovalTaskController {
    private final ApprovalService approvals;
    private final BusinessApprovalService businesses;

    public ApprovalTaskController(ApprovalService approvals, BusinessApprovalService businesses) {
        this.approvals = approvals;
        this.businesses = businesses;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('page:approval')")
    public Result<List<ApprovalTaskVO>> list(Authentication auth) {
        return Result.ok(approvals.tasks(auth.getName()));
    }

    /** 已办结审批历史：管理员看全部，普通用户看自己审批过的 + 自己申请的。 */
    @GetMapping("/history")
    @PreAuthorize("hasAuthority('page:approval')")
    public Result<org.springframework.data.domain.Page<com.wms.dto.ApprovalRecordVO>> history(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        return Result.ok(approvals.history(auth.getName(), type, status, keyword, page, size));
    }

    @PostMapping("/{type}/{id}/approve")
    @PreAuthorize("hasAuthority('outbound:approve')")
    public Result<Map<String, Object>> approve(@PathVariable String type, @PathVariable Long id,
                                               @RequestBody(required = false) Map<String, String> body,
                                               Authentication auth) {
        return Result.ok(businesses.review(type, id, true, body == null ? null : body.get("opinion"), auth.getName()));
    }

    @PostMapping("/{type}/{id}/reject")
    @PreAuthorize("hasAuthority('outbound:approve')")
    public Result<Map<String, Object>> reject(@PathVariable String type, @PathVariable Long id,
                                              @RequestBody(required = false) Map<String, String> body,
                                              Authentication auth) {
        return Result.ok(businesses.review(type, id, false, body == null ? null : body.get("opinion"), auth.getName()));
    }
}
