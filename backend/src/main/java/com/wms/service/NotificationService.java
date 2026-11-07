package com.wms.service;

import com.wms.dto.ApprovalTaskVO;
import com.wms.dto.NotificationItem;
import com.wms.dto.NotificationReadRequest;
import com.wms.entity.NotificationRead;
import com.wms.repository.NotificationReadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * 站内消息中心（仅限通知类，不包含待办）：
 *   - ALERT：备件预警（库存不足/批次临期/更换预警/需求预测），被动产生的通知
 *   - APPROVAL：有人发起领用/退库/报废/盘点差异复核，分派给当前用户处理
 *
 * <p>拣货/盘点进行中属于业务待办，归工作台统一呈现，不进消息中心。</p>
 *
 * <p><b>已读语义（和常见 APP 一致）</b><br>
 * 已读不等于消息消失：消息继续挂在弹框里，只是不再出红点、不再计数。<br>
 *   - 预警：已读/未读直接用 wms_alert_user_state.read_flag（用户维度），
 *     预警被处理/忽略后自然离开列表；<br>
 *   - 审批等其它分类：已读回执落在 wms_notification_read 并快照内容，
 *     即使审批已经处理完、源任务消失，消息仍能以"已读"留在列表里。</p>
 */
@Service
public class NotificationService {
    /** 已读历史回看窗口：更早的消息不再占用弹框 */
    private static final Duration HISTORY_WINDOW = Duration.ofDays(30);
    /** 弹框最多返回多少条 */
    private static final int MAX_ITEMS = 60;

    private final AlertService alertService;
    private final ApprovalService approvalService;
    private final NotificationReadRepository readRepo;

    public NotificationService(AlertService alertService, ApprovalService approvalService,
                               NotificationReadRepository readRepo) {
        this.alertService = alertService;
        this.approvalService = approvalService;
        this.readRepo = readRepo;
    }

    // ==================== 查询 ====================

    /** 消息列表：未读在前按时间倒序，已读的排在后面但依然保留 */
    @Transactional(readOnly = true)
    public List<NotificationItem> list(String username) {
        List<NotificationItem> live = new ArrayList<>();
        live.addAll(alertItems());
        live.addAll(approvalItems(username));

        Set<String> liveKeys = new HashSet<>();
        for (NotificationItem it : live) liveKeys.add(key(it));

        // 源数据已消失、但用户已读过的消息，继续挂在列表里（例如已处理完的审批）
        List<NotificationItem> history = new ArrayList<>();
        for (NotificationRead r : readRepo.findByUsernameAndReadAtAfterOrderByReadAtDesc(
                username, Instant.now().minus(HISTORY_WINDOW))) {
            if (liveKeys.contains(r.getCategory() + "#" + r.getTargetId())) continue;
            history.add(new NotificationItem(
                    r.getCategory(),
                    r.getSeverity() == null ? "INFO" : r.getSeverity(),
                    r.getTitle() == null ? "消息" : r.getTitle(),
                    r.getSubtitle() == null ? "" : r.getSubtitle(),
                    r.getMessage() == null ? "" : r.getMessage(),
                    r.getRoute() == null ? "/workbench" : r.getRoute(),
                    r.getTargetId(),
                    r.getSourceCreatedAt(),
                    true
            ));
        }

        List<NotificationItem> out = new ArrayList<>(live);
        out.addAll(history);
        // 未读优先，其次按产生时间倒序；时间缺失的排最后
        out.sort((a, b) -> {
            if (a.read() != b.read()) return a.read() ? 1 : -1;
            Instant ta = a.createdAt() == null ? Instant.MIN : a.createdAt();
            Instant tb = b.createdAt() == null ? Instant.MIN : b.createdAt();
            return tb.compareTo(ta);
        });
        return out.size() > MAX_ITEMS ? new ArrayList<>(out.subList(0, MAX_ITEMS)) : out;
    }

    /** 铃铛红点：未读预警 + 未读待审批（已读的不算） */
    @Transactional(readOnly = true)
    public long totalUnread(String username) {
        return alertService.unreadCount() + unreadApprovalCount(username);
    }

    // ==================== 已读操作 ====================

    /** 单条已读：预警写用户状态位，其它分类写已读回执（带内容快照） */
    @Transactional
    public void markRead(String username, NotificationReadRequest req) {
        if (req == null || req.category() == null || req.targetId() == null) return;
        String category = req.category();

        if ("ALERT".equals(category)) {
            // 预警本来就有用户维度已读位，写它即可，不需要再存回执
            alertService.markRead(req.targetId());
            return;
        }

        List<NotificationItem> live = new ArrayList<>();
        live.addAll(alertItems());
        live.addAll(approvalItems(username));
        NotificationItem found = live.stream()
                .filter(i -> category.equals(i.category()) && req.targetId().equals(i.targetId()))
                .findFirst()
                .orElse(null);

        Instant now = Instant.now();
        upsertReceipt(username, category, req.targetId(),
                found != null ? found : fromRequest(req), now);
        readRepo.deleteExpired(now.minus(HISTORY_WINDOW));
    }

    /** 全部已读：预警走原有批量已读，待审批逐条写回执 */
    @Transactional
    public void markAllRead(String username) {
        alertService.markAllRead();
        Instant now = Instant.now();
        for (NotificationItem item : approvalItems(username)) {
            if (item.read()) continue;
            upsertReceipt(username, item.category(), item.targetId(), item, now);
        }
        readRepo.deleteExpired(now.minus(HISTORY_WINDOW));
    }

    private void upsertReceipt(String username, String category, Long targetId,
                               NotificationItem src, Instant now) {
        if (username == null || username.isBlank() || targetId == null) return;
        NotificationRead r = readRepo
                .findByUsernameAndCategoryAndTargetId(username, category, targetId)
                .orElseGet(() -> {
                    NotificationRead fresh = new NotificationRead();
                    fresh.setUsername(username);
                    fresh.setCategory(category);
                    fresh.setTargetId(targetId);
                    return fresh;
                });
        r.setSeverity(cut(src.severity(), 16));
        r.setTitle(cut(src.title(), 128));
        r.setSubtitle(cut(src.subtitle(), 255));
        r.setMessage(cut(src.message(), 512));
        r.setRoute(cut(src.route(), 128));
        r.setSourceCreatedAt(src.createdAt());
        r.setReadAt(now);
        readRepo.save(r);
    }

    private static NotificationItem fromRequest(NotificationReadRequest req) {
        return new NotificationItem(req.category(), req.severity(), req.title(), req.subtitle(),
                req.message(), req.route(), req.targetId(), req.createdAt(), true);
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String key(NotificationItem it) {
        return it.category() + "#" + it.targetId();
    }

    // ==================== 各分类数据源 ====================

    // ---------- 预警 ----------
    private List<NotificationItem> alertItems() {
        List<Map<String, Object>> rows = alertService.inbox("OPEN");
        List<NotificationItem> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            String alertType = String.valueOf(r.getOrDefault("alertType", ""));
            String typeText = switch (alertType) {
                case "STOCK" -> "库存不足";
                case "EXPIRY" -> "批次临期";
                case "LIFECYCLE" -> "更换预警";
                case "FORECAST" -> "需求预测";
                default -> "备件预警";
            };
            String sku = String.valueOf(r.getOrDefault("sku", ""));
            String name = String.valueOf(r.getOrDefault("productName", ""));
            boolean read = Boolean.TRUE.equals(r.get("readFlag"));
            out.add(new NotificationItem(
                    "ALERT",
                    String.valueOf(r.getOrDefault("severity", "MEDIUM")),
                    typeText,
                    sku + " · " + name,
                    String.valueOf(r.getOrDefault("message", "")),
                    "/alerts",
                    ((Number) r.get("id")).longValue(),
                    (Instant) r.get("updatedAt"),
                    read
            ));
        }
        return out;
    }

    // ---------- 待审批 ----------
    private List<NotificationItem> approvalItems(String username) {
        if (username == null || username.isBlank()) return List.of();
        Set<Long> readIds = readIds(username, "APPROVAL");
        List<ApprovalTaskVO> tasks = approvalService.tasks(username);
        List<NotificationItem> out = new ArrayList<>();
        for (ApprovalTaskVO t : tasks) {
            String bizLabel = switch (t.businessType()) {
                case "OUTBOUND" -> "领用申请";
                case "RETURN" -> "退库申请";
                case "SCRAP" -> "报废申请";
                case "STOCKTAKE_DIFF" -> "盘点差异复核";
                default -> "审批任务";
            };
            boolean overdue = t.overdue();
            out.add(new NotificationItem(
                    "APPROVAL",
                    overdue ? "HIGH" : "MEDIUM",
                    bizLabel + (overdue ? " · 已超时" : ""),
                    t.businessNo() + " · 申请人 " + t.applicant(),
                    t.matchReason() != null ? t.matchReason() : "等待您处理",
                    "/approvals",
                    t.businessId(),
                    t.submittedAt(),
                    readIds.contains(t.businessId())
            ));
        }
        return out;
    }

    private long unreadApprovalCount(String username) {
        if (username == null || username.isBlank()) return 0;
        Set<Long> readIds = readIds(username, "APPROVAL");
        return approvalService.tasks(username).stream()
                .filter(t -> !readIds.contains(t.businessId()))
                .count();
    }

    private Set<Long> readIds(String username, String category) {
        Set<Long> ids = new HashSet<>();
        if (username == null || username.isBlank()) return ids;
        for (NotificationRead r : readRepo.findByUsernameAndCategory(username, category)) {
            ids.add(r.getTargetId());
        }
        return ids;
    }
}
