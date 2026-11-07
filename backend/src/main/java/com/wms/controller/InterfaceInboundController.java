package com.wms.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.common.Result;
import com.wms.service.InterfaceLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 外部系统上报报文入口。报文原样落库到接口日志，供「系统管理-接口管理-接收报文」查询。
 */
@RestController
@RequestMapping("/api/interface")
public class InterfaceInboundController {
    private final InterfaceLogService logService;
    private final ObjectMapper objectMapper;

    public InterfaceInboundController(InterfaceLogService logService, ObjectMapper objectMapper) {
        this.logService = logService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/inbound")
    public Result<Map<String, Object>> receive(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        long started = System.currentTimeMillis();
        String code = text(body.get("interfaceCode"));
        if (!StringUtils.hasText(code)) {
            return Result.fail("interfaceCode 不能为空");
        }
        String name = text(body.get("interfaceName"));
        String channel = StringUtils.hasText(text(body.get("channel"))) ? text(body.get("channel")) : "HTTP";
        String peer = StringUtils.hasText(text(body.get("peer"))) ? text(body.get("peer")) : request.getRemoteAddr();
        String traceId = StringUtils.hasText(text(body.get("traceId")))
                ? text(body.get("traceId"))
                : UUID.randomUUID().toString().replace("-", "");
        Object payload = body.containsKey("payload") ? body.get("payload") : body;

        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("received", true);
        ack.put("traceId", traceId);

        logService.recordInbound(code, name, channel, peer, toJson(payload), toJson(ack),
                true, null, traceId, System.currentTimeMillis() - started);
        return Result.ok(ack);
    }

    private String text(Object value) { return value == null ? null : String.valueOf(value); }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
