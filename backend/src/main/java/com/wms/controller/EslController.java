package com.wms.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.common.Result;
import com.wms.config.EslProperties;
import com.wms.dto.esl.EslButtonPressCallbackDTO;
import com.wms.dto.esl.EslLightLedBatchReq;
import com.wms.dto.esl.EslLightLedItemDTO;
import com.wms.dto.esl.EslLightLedResultCallbackDTO;
import com.wms.entity.Location;
import com.wms.entity.OutboundItem;
import com.wms.repository.LocationRepository;
import com.wms.repository.OutboundItemRepository;
import com.wms.service.EsTagService;
import com.wms.service.EslService;
import com.wms.service.InterfaceLogService;
import com.wms.service.OperationAuditService;
import com.wms.service.OutboundService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 仓储 13 代电子标签（墨水屏）相关接口。
 * <p>
 * 分两类：
 * 1. 业务主动调用（WMS → 墨水屏服务器）：带 JWT 鉴权，会校验 MAC 是否已绑定库位
 * 2. 外部回调（墨水屏服务器 → WMS）：/api/esl/callback/** 在 SecurityConfig 已放行
 */
@RestController
@RequestMapping("/api/esl")
public class EslController {

    private final EslService eslService;
    private final InterfaceLogService logService;
    private final LocationRepository locationRepo;
    private final OutboundService outboundService;
    private final OutboundItemRepository outboundItemRepo;
    private final EsTagService esTagService;
    private final OperationAuditService auditService;
    private final ObjectMapper objectMapper;
    private final EslProperties eslProperties;

    public EslController(EslService eslService,
                         InterfaceLogService logService,
                         LocationRepository locationRepo,
                         OutboundService outboundService,
                         OutboundItemRepository outboundItemRepo,
                         EsTagService esTagService,
                         OperationAuditService auditService,
                         ObjectMapper objectMapper,
                         EslProperties eslProperties) {
        this.eslService = eslService;
        this.logService = logService;
        this.locationRepo = locationRepo;
        this.outboundService = outboundService;
        this.outboundItemRepo = outboundItemRepo;
        this.esTagService = esTagService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.eslProperties = eslProperties;
    }

    /**
     * 回调鉴权。
     *
     * <p>{@code /api/esl/callback/**} 在 Spring Security 里必须 permitAll（设备端带不了 JWT），
     * 于是这里用共享密钥补上"调用方是不是那台墨水屏服务器"。
     * 配置了 {@code eslabel.callback-token} 才校验，留空表示不校验（现场还没配时的兼容路径，
     * 启动日志会提示）。
     *
     * <p>用 {@link MessageDigest#isEqual} 定长比较，避免逐字节短路比较带来的时间差。
     */
    private boolean callbackAllowed(HttpServletRequest request) {
        String expected = eslProperties.getCallbackToken();
        if (expected == null || expected.isBlank()) return true;
        String actual = request.getHeader(eslProperties.getCallbackHeader());
        if (actual == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    /** 鉴权失败时的统一 ACK：不回执成功、写接口日志与审计，便于事后追查谁在伪造回调 */
    private Map<String, Object> rejectCallback(String interfaceCode, String interfaceName,
                                               String peer, String jsonBody,
                                               Map<String, Object> ack, String traceId, long started) {
        String msg = "回调鉴权失败：缺少或错误的请求头 " + eslProperties.getCallbackHeader();
        ack.put("received", false);
        ack.put("message", msg);
        logService.recordInbound(interfaceCode, interfaceName, "HTTP", peer, jsonBody, toJson(ack),
                false, msg, traceId, System.currentTimeMillis() - started);
        try {
            auditService.record("ESL_CALLBACK", 0L, interfaceCode, "REJECT", null, null,
                    "esl-callback", msg + "；来源 IP：" + peer);
        } catch (RuntimeException ignore) {
            // 审计写失败不能影响回调返回
        }
        return ack;
    }

    // ========================================================================
    // 1. 业务方主动调用 —— 批量点灯
    // ========================================================================

    /** 批量点灯（支持标准 / 移动场景切换）。 */
    @PostMapping("/light")
    public Result<Map<String, Object>> lightBatch(@RequestBody @Valid EslLightLedBatchReq req) {
        if (req.getItems() == null || req.getItems().isEmpty()) return Result.fail("items 不能为空");
        Result<Void> vr = validateMacs(req.getItems());
        if (vr != null) return Result.fail(vr.getMsg());
        boolean ok = eslService.lightTagsLed(req);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("moving", Boolean.TRUE.equals(req.getMoving()));
        data.put("count", req.getItems().size());
        data.put("success", ok);
        return ok ? Result.ok(data) : Result.fail("电子标签服务器返回失败");
    }

    /** 批量点灯（直接传数组，默认标准场景）。 */
    @PostMapping("/light/items")
    public Result<Map<String, Object>> lightItems(@RequestBody @Valid List<EslLightLedItemDTO> items) {
        if (items.isEmpty()) return Result.fail("items 不能为空");
        Result<Void> vr = validateMacs(items);
        if (vr != null) return Result.fail(vr.getMsg());
        boolean ok = eslService.lightTagsLed(items);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("moving", false);
        data.put("count", items.size());
        data.put("success", ok);
        return ok ? Result.ok(data) : Result.fail("电子标签服务器返回失败");
    }

    /** 批量点灯（移动场景专用）。 */
    @PostMapping("/light/moving")
    public Result<Map<String, Object>> lightMoving(@RequestBody @Valid List<EslLightLedItemDTO> items) {
        if (items.isEmpty()) return Result.fail("items 不能为空");
        Result<Void> vr = validateMacs(items);
        if (vr != null) return Result.fail(vr.getMsg());
        boolean ok = eslService.lightMovingTagsLed(items);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("moving", true);
        data.put("count", items.size());
        data.put("success", ok);
        return ok ? Result.ok(data) : Result.fail("电子标签服务器（移动场景）返回失败");
    }

    /**
     * 快捷：单标签点亮（URL 参数）。适合页面"点亮测试"按钮直接调用。
     *
     * @param mac     标签 MAC
     * @param rgb     颜色，默认 ff0000（红）；支持 red/green/blue/yellow/white/purple/light_blue/off
     * @param mode    模式，默认 0 常亮；0常亮/1快闪/2慢闪/3灭灯/4全灭
     * @param timeout 超时秒，默认 30；0 表示不自动熄灭
     */
    @PostMapping("/light/{mac}")
    public Result<Map<String, Object>> lightOne(@PathVariable String mac,
                                                 @RequestParam(defaultValue = "ff0000") String rgb,
                                                 @RequestParam(defaultValue = "0") int mode,
                                                 @RequestParam(defaultValue = "30") int timeout) {
        EslLightLedItemDTO item = new EslLightLedItemDTO();
        item.setMac(mac);
        item.setLedrgb(rgb);
        item.setLedmode(mode);
        item.setTimeout(timeout);
        return lightItems(List.of(item));
    }

    /** 快捷：单标签熄灭（等价于 light/{mac}?mode=4）。 */
    @PostMapping("/light/{mac}/off")
    public Result<Map<String, Object>> lightOffOne(@PathVariable String mac) {
        EslLightLedItemDTO item = new EslLightLedItemDTO();
        item.setMac(mac);
        item.setLedmode(4);  // 4 = 全灭
        item.setLedrgb("");
        item.setTimeout(0);
        return lightItems(List.of(item));
    }

    // ========================================================================
    // 2. 外部回调 —— 墨水屏服务器 → WMS（需在 SecurityConfig 放行）
    // ========================================================================

    /**
     * 单独点灯接口结果回调（ctrlShelfIndicatorResult）。
     * <p>
     * 业务联动：
     * <ol>
     *   <li>按 cmdtoken 反查 startPicking 时发出的出库项，回填 lightConfirmed / lightMessage</li>
     *   <li>同步回调携带的电量 power 到 EsTag，顺带刷新 lastSeenAt / online</li>
     *   <li>写一条操作审计日志</li>
     * </ol>
     * 以上任何一步失败不影响主流程返回 ACK，只记 warn 日志。
     */
    @PostMapping("/callback/light-result")
    public Map<String, Object> lightResultCallback(@RequestBody EslLightLedResultCallbackDTO body,
                                                    HttpServletRequest request) {
        long started = System.currentTimeMillis();
        String peer = request.getRemoteAddr();
        String traceId = UUID.randomUUID().toString().replace("-", "");

        String jsonBody = toJson(body);
        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("traceId", traceId);

        if (!callbackAllowed(request)) {
            return rejectCallback(EslService.CODE_LIGHT_TAGS_LED_CALLBACK, "电子标签点灯结果回调",
                    peer, jsonBody, ack, traceId, started);
        }

        boolean bound = body.getMac() != null && locationRepo.findByDeviceNo(body.getMac()) != null;
        ack.put("received", true);
        ack.put("success", bound);  // 基础合法性：MAC 必须绑定库位才认为"成功接收"
        ack.put("message", bound ? "ok" : "标签 MAC 未绑定任何库位：" + body.getMac());

        if (bound) {
            // —— 1. cmdtoken → OutboundItem 反查 & 回填 —— //
            String cmdtoken = body.getCmdtoken();
            if (cmdtoken != null && !cmdtoken.isBlank()) {
                try {
                    outboundItemRepo.findByLightCmdtoken(cmdtoken).ifPresent(item -> {
                        int confirmed = Boolean.TRUE.equals(body.getResult()) ? 1 : 2;
                        item.setLightConfirmed(confirmed);
                        item.setLightConfirmedAt(Instant.now());
                        item.setLightMessage(body.getMessage());
                        outboundItemRepo.save(item);

                        // 审计日志 —— 写在 ifPresent 里，避免无匹配时产生垃圾日志
                        auditService.record("OUTBOUND_ITEM", item.getId(),
                                item.getOutboundOrder() != null && item.getOutboundOrder().getOrderNo() != null
                                        ? item.getOutboundOrder().getOrderNo() : null,
                                "LIGHT_RESULT_CALLBACK",
                                null, null,
                                String.format("标签 MAC %s，result=%s，message=%s",
                                        body.getMac(), body.getResult(), body.getMessage()));
                    });
                    // 没 ifPresent 到也只是 cmdtoken 没人用，不影响主流程
                } catch (Exception ex) {
                    // 业务联动失败不能让回调整体挂掉 —— 墨水屏服务器 ACK 了就行
                    org.slf4j.LoggerFactory.getLogger(EslController.class).warn(
                            "[ESL] 亮灯结果回调业务联动失败：cmdtoken={}, mac={}, err={}",
                            cmdtoken, body.getMac(), ex.getMessage(), ex);
                }
            }

            // —— 2. 同步电量 / 在线状态到 EsTag —— //
            if (body.getPower() != null) {
                try {
                    esTagService.syncRuntime(body.getMac(), body.getPower(), true, null);
                } catch (Exception ex) {
                    org.slf4j.LoggerFactory.getLogger(EslController.class).warn(
                            "[ESL] 亮灯结果回调电量同步失败：mac={}, err={}", body.getMac(), ex.getMessage(), ex);
                }
            }
        }

        logService.recordInbound(
                EslService.CODE_LIGHT_TAGS_LED_CALLBACK,
                "电子标签点灯结果回调",
                "HTTP", peer,
                jsonBody, toJson(ack),
                true, null, traceId, System.currentTimeMillis() - started
        );
        return ack;
    }

    /**
     * 按键事件回调（enterKey）。
     * <p>
     * 电子标签右下按键 / 读卡器触发后，墨水屏服务器 POST 到这里。
     * WMS 会根据 MAC 找到绑定的库位 → 找到当前处于 PICKING 状态的出库项，
     * 将其 button_pressed 置 true。completePicking() 会以此作为必要条件校验。
     */
    @PostMapping("/callback/button-press")
    public Map<String, Object> buttonPressCallback(@RequestBody EslButtonPressCallbackDTO body,
                                                   HttpServletRequest request) {
        long started = System.currentTimeMillis();
        String peer = request.getRemoteAddr();
        String traceId = UUID.randomUUID().toString().replace("-", "");
        String jsonBody = toJson(body);

        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("traceId", traceId);

        if (!callbackAllowed(request)) {
            return rejectCallback(EslService.CODE_BUTTON_PRESS_CALLBACK, "电子标签按键事件回调",
                    peer, jsonBody, ack, traceId, started);
        }

        if (body.getMac() == null) {
            String errMsg = "回调报文缺少 mac 字段";
            ack.put("received", false);
            ack.put("message", errMsg);
            logService.recordInbound(
                    EslService.CODE_BUTTON_PRESS_CALLBACK,
                    "电子标签按键事件回调",
                    "HTTP", peer, jsonBody, toJson(ack),
                    false, errMsg, traceId, System.currentTimeMillis() - started);
            return ack;
        }

        Location loc = locationRepo.findByDeviceNo(body.getMac());
        if (loc == null) {
            String errMsg = "标签 MAC 未绑定任何库位（Location.deviceNo）：" + body.getMac();
            ack.put("received", false);
            ack.put("matchedItems", 0);
            ack.put("message", errMsg);
            logService.recordInbound(
                    EslService.CODE_BUTTON_PRESS_CALLBACK,
                    "电子标签按键事件回调",
                    "HTTP", peer, jsonBody, toJson(ack),
                    false, errMsg, traceId, System.currentTimeMillis() - started);
            return ack;
        }

        int matched = outboundService.markButtonPressed(body.getMac(), body.getResult());
        ack.put("received", true);
        ack.put("matchedItems", matched);
        if (matched == 0) {
            // MAC 绑定了库位但库位上当前没有 PICKING 的出库项 —— 比如业务流程还没走到开始拣货
            ack.put("message", "MAC 已绑定库位 " + loc.getCode() + "，但当前无 PICKING 状态出库项需要按键确认");
        } else {
            ack.put("message", "ok");
        }

        boolean success = matched > 0;
        logService.recordInbound(
                EslService.CODE_BUTTON_PRESS_CALLBACK,
                "电子标签按键事件回调",
                "HTTP", peer, jsonBody, toJson(ack),
                success,
                success ? null : "未找到可匹配的 PICKING 出库项",
                traceId, System.currentTimeMillis() - started);
        return ack;
    }

    // ========================================================================
    // 私有工具
    // ========================================================================

    /** 校验每个 item 的 mac 都已绑定到 Location.deviceNo。null = 全部合法 */
    private Result<Void> validateMacs(List<EslLightLedItemDTO> items) {
        List<String> missing = new ArrayList<>();
        for (EslLightLedItemDTO it : items) {
            if (locationRepo.findByDeviceNo(it.getMac()) == null) missing.add(it.getMac());
        }
        if (!missing.isEmpty()) return Result.fail("以下标签 MAC 未绑定任何库位（Location.deviceNo）：" + String.join(", ", missing));
        return null;
    }

    private String toJson(Object v) {
        try { return objectMapper.writeValueAsString(v); }
        catch (JsonProcessingException e) { return String.valueOf(v); }
    }
}
