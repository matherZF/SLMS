package com.wms.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

/**
 * 系统与外部系统的接口报文日志。
 * direction=INBOUND 表示外部系统发来的报文，OUTBOUND 表示本系统发出的报文。
 */
@Entity
@Table(name = "wms_interface_log", indexes = {
        @Index(name = "idx_interface_direction_time", columnList = "direction,created_at"),
        @Index(name = "idx_interface_code", columnList = "interface_code"),
        @Index(name = "idx_interface_trace", columnList = "trace_id")
})
public class InterfaceLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 16) private String direction;
    @Column(name = "interface_code", nullable = false, length = 64) private String interfaceCode;
    @Column(name = "interface_name", length = 128) private String interfaceName;
    @Column(length = 16) private String channel;
    @Column(length = 128) private String peer;
    @Column(name = "request_body", columnDefinition = "longtext") private String requestBody;
    @Column(name = "response_body", columnDefinition = "longtext") private String responseBody;
    @Column(nullable = false) private boolean success;
    @Column(name = "error_message", length = 500) private String errorMessage;
    @Column(name = "trace_id", length = 64) private String traceId;
    @Column(name = "duration_ms") private Long durationMs;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private Instant createdAt;

    public Long getId() { return id; }
    public String getDirection() { return direction; } public void setDirection(String v) { direction = v; }
    public String getInterfaceCode() { return interfaceCode; } public void setInterfaceCode(String v) { interfaceCode = v; }
    public String getInterfaceName() { return interfaceName; } public void setInterfaceName(String v) { interfaceName = v; }
    public String getChannel() { return channel; } public void setChannel(String v) { channel = v; }
    public String getPeer() { return peer; } public void setPeer(String v) { peer = v; }
    public String getRequestBody() { return requestBody; } public void setRequestBody(String v) { requestBody = v; }
    public String getResponseBody() { return responseBody; } public void setResponseBody(String v) { responseBody = v; }
    public boolean isSuccess() { return success; } public void setSuccess(boolean v) { success = v; }
    public String getErrorMessage() { return errorMessage; } public void setErrorMessage(String v) { errorMessage = v; }
    public String getTraceId() { return traceId; } public void setTraceId(String v) { traceId = v; }
    public Long getDurationMs() { return durationMs; } public void setDurationMs(Long v) { durationMs = v; }
    public Instant getCreatedAt() { return createdAt; }
}
