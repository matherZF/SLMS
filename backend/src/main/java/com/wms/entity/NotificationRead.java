package com.wms.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 站内消息已读回执：每个用户独立，已读后消息仍留在列表里，只是不再出红点。
 * 同时快照标题/正文，源数据消失（如审批已处理）后仍能按已读展示。
 */
@Entity
@Table(name = "wms_notification_read", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"username", "category", "targetId"})
})
public class NotificationRead {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(nullable = false, length = 16)
    private String category;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(length = 16)
    private String severity;

    @Column(length = 128)
    private String title;

    @Column(length = 255)
    private String subtitle;

    @Column(length = 512)
    private String message;

    @Column(length = 128)
    private String route;

    @Column(name = "source_created_at")
    private Instant sourceCreatedAt;

    @Column(name = "read_at", nullable = false)
    private Instant readAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }
    public Instant getSourceCreatedAt() { return sourceCreatedAt; }
    public void setSourceCreatedAt(Instant sourceCreatedAt) { this.sourceCreatedAt = sourceCreatedAt; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
}
