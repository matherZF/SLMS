package com.wms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.config.EslProperties;
import com.wms.dto.esl.EslLightLedBatchReq;
import com.wms.dto.esl.EslLightLedItemDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 仓储 13 代电子标签（墨水屏）客户端服务。
 * <p>
 * 对外提供「单独点灯」等能力，内部通过 RestTemplate 调用墨水屏服务器 HTTP 接口，
 * 并将每次出站调用记录到接口日志表，便于后续问题追踪。
 */
@Service
public class EslService {

    private static final Logger log = LoggerFactory.getLogger(EslService.class);

    /** 接口编码，用于 InterfaceLogService 打分类 */
    public static final String CODE_LIGHT_TAGS_LED   = "ESL_LIGHT_TAGS_LED";
    public static final String CODE_LIGHT_TAGS_LED_CALLBACK = "ESL_LIGHT_TAGS_LED_CALLBACK";
    /** 按键事件回调（enterKey） —— completePicking() 必要条件的驱动源 */
    public static final String CODE_BUTTON_PRESS_CALLBACK = "ESL_BUTTON_PRESS_CALLBACK";
    /** 查询电子标签信息（deviceInfo） */
    public static final String CODE_QUERY_DEVICE_INFO = "ESL_QUERY_DEVICE_INFO";

    private final RestTemplate restTemplate;
    private final EslProperties props;
    private final InterfaceLogService logService;
    private final ObjectMapper objectMapper;

    public EslService(@Qualifier("eslRestTemplate") RestTemplate restTemplate,
                      EslProperties props,
                      InterfaceLogService logService,
                      ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.props = props;
        this.logService = logService;
        this.objectMapper = objectMapper;
    }

    /**
     * 批量点灯（标准场景）。
     *
     * @param items 点灯明细，至少 1 条
     * @return true = 墨水屏服务器返回成功；false 表示请求失败或服务器返回非 true
     */
    public boolean lightTagsLed(List<EslLightLedItemDTO> items) {
        return doLight(items, false);
    }

    /**
     * 批量点灯（移动场景专用 /lightMovingTagsLed/v2）。
     */
    public boolean lightMovingTagsLed(List<EslLightLedItemDTO> items) {
        return doLight(items, true);
    }

    /**
     * 根据请求体批量点灯。
     */
    public boolean lightTagsLed(EslLightLedBatchReq req) {
        if (req == null || req.getItems() == null || req.getItems().isEmpty()) {
            log.warn("[ESL] 批量点灯 items 为空，跳过");
            return false;
        }
        boolean moving = Boolean.TRUE.equals(req.getMoving());
        return doLight(req.getItems(), moving);
    }

    private boolean doLight(List<EslLightLedItemDTO> items, boolean moving) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        String url = props.fullLightUrl(moving);
        String interfaceCode = moving ? CODE_LIGHT_TAGS_LED + "_MOVING" : CODE_LIGHT_TAGS_LED;
        String interfaceName = moving ? "电子标签点灯（移动场景）" : "电子标签单独点灯";
        long started = System.currentTimeMillis();

        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            log.error("[ESL] 请求体序列化失败 traceId={}: {}", traceId, e.getMessage());
            return false;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

        String responseJson = null;
        boolean success = false;
        String errorMsg = null;
        String peer = props.getServerUrl();

        try {
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            responseJson = resp.getBody();
            // 文档明确返回值为 true/false
            success = resp.getStatusCode().is2xxSuccessful()
                    && StringUtils.hasText(responseJson)
                    && responseJson.trim().equalsIgnoreCase("true");
            if (!success) {
                errorMsg = "墨水屏服务器返回非 true，body=" + responseJson;
                log.warn("[ESL] 点灯响应非成功 traceId={} status={} body={}",
                        traceId, resp.getStatusCode(), responseJson);
            }
        } catch (RestClientException e) {
            errorMsg = "HTTP 调用失败: " + e.getMessage();
            log.error("[ESL] 点灯请求异常 traceId={} url={}: {}", traceId, url, e.getMessage());
        }

        logService.recordOutbound(
                interfaceCode, interfaceName, "HTTP", peer,
                requestJson, responseJson,
                success, errorMsg, traceId, System.currentTimeMillis() - started
        );
        return success;
    }

    /**
     * 查询电子标签信息（电量、在线、固件版本等）。
     *
     * @param macs 要查询的 MAC 列表（空 = 查全部？取决于墨水屏服务器实现，这里不做空列表判断）
     * @return Map：
     *         <ul>
     *           <li>success: boolean</li>
     *           <li>peer: 墨水屏服务器地址</li>
     *           <li>items: List&lt;Map&gt;，每个元素至少包含 mac、power、online、firmwareVersion</li>
     *         </ul>
     */
    public Map<String, Object> queryDeviceInfo(List<String> macs) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("items", List.of());
        String traceId = UUID.randomUUID().toString().replace("-", "");
        String url = props.fullQueryUrl();
        long started = System.currentTimeMillis();
        String peer = props.getServerUrl();

        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(macs == null ? List.of() : macs);
        } catch (JsonProcessingException e) {
            log.error("[ESL] 查询标签信息请求体序列化失败 traceId={}: {}", traceId, e.getMessage());
            result.put("error", e.getMessage());
            return result;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

        String responseJson = null;
        String errorMsg = null;

        try {
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            responseJson = resp.getBody();
            // 墨水屏服务器返回格式不确定 —— 可能是 JSON 数组、也可能是单个对象
            Object parsed = StringUtils.hasText(responseJson) ? objectMapper.readValue(responseJson, Object.class) : null;
            List<Map<String, Object>> items = new java.util.ArrayList<>();
            if (parsed instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) items.add(new LinkedHashMap<>((Map<String, Object>) m));
                }
            } else if (parsed instanceof Map<?, ?> m) {
                items.add(new LinkedHashMap<>((Map<String, Object>) m));
            }
            result.put("success", resp.getStatusCode().is2xxSuccessful());
            result.put("items", items);
        } catch (RestClientException e) {
            errorMsg = "HTTP 调用失败: " + e.getMessage();
            log.error("[ESL] 查询标签信息异常 traceId={} url={}: {}", traceId, url, e.getMessage());
            result.put("error", errorMsg);
        } catch (JsonProcessingException e) {
            errorMsg = "响应 JSON 解析失败: " + e.getMessage();
            log.warn("[ESL] 查询标签信息响应非 JSON traceId={}: {}", traceId, e.getMessage());
            result.put("error", errorMsg);
            result.put("rawResponse", responseJson);
        }

        logService.recordOutbound(
                CODE_QUERY_DEVICE_INFO, "电子标签信息查询", "HTTP", peer,
                requestJson, responseJson,
                result.get("success") == Boolean.TRUE, errorMsg, traceId, System.currentTimeMillis() - started
        );
        return result;
    }
}
