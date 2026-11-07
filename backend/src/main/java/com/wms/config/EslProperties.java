package com.wms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 仓储 13 代电子标签（墨水屏）服务器配置。
 */
@Configuration
@ConfigurationProperties(prefix = "eslabel")
public class EslProperties {

    private static final Logger log = LoggerFactory.getLogger(EslProperties.class);

    /** 墨水屏服务器根地址 */
    private String serverUrl = "http://192.168.1.200";

    /** 标准场景 - 单独点灯接口路径 */
    private String lightPath = "/wms/associate/lightTagsLed";

    /** 移动场景 - 单独点灯接口路径 */
    private String lightMovingPath = "/wms/associate/lightMovingTagsLed/v2";

    /** 查询电子标签信息接口路径（MAC 列表 POST，返回电量/在线/固件等） */
    private String queryDeviceInfoPath = "/wms/associate/deviceInfo";

    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 5000;

    /**
     * 回调共享密钥。
     *
     * <p>{@code /api/esl/callback/**} 在 Spring Security 里是 permitAll（设备无法带 JWT），
     * 如果不做任何校验，同网段任何人都能伪造"按键已按下"——而按键是完成出库的唯一物理关卡。
     * 配置后回调必须携带同名请求头且值一致；留空表示不校验（兼容现场尚未配置的设备端）。
     */
    private String callbackToken = "";

    /** 回调携带密钥的请求头名，默认 X-ESL-Token */
    private String callbackHeader = "X-ESL-Token";

    @PostConstruct
    void warnIfCallbackTokenMissing() {
        if (callbackToken == null || callbackToken.isBlank()) {
            log.warn("[ESL] 未配置 eslabel.callback-token（环境变量 ESL_CALLBACK_TOKEN）："
                    + "/api/esl/callback/** 当前不做任何鉴权，任何能访问本服务的人都可以伪造按键/亮灯回调");
        }
    }

    public String getServerUrl() { return serverUrl; }
    public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }
    public String getLightPath() { return lightPath; }
    public void setLightPath(String lightPath) { this.lightPath = lightPath; }
    public String getLightMovingPath() { return lightMovingPath; }
    public void setLightMovingPath(String lightMovingPath) { this.lightMovingPath = lightMovingPath; }
    public String getQueryDeviceInfoPath() { return queryDeviceInfoPath; }
    public void setQueryDeviceInfoPath(String queryDeviceInfoPath) { this.queryDeviceInfoPath = queryDeviceInfoPath; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    public String getCallbackToken() { return callbackToken; }
    public void setCallbackToken(String callbackToken) { this.callbackToken = callbackToken == null ? "" : callbackToken.trim(); }
    public String getCallbackHeader() { return callbackHeader; }
    public void setCallbackHeader(String callbackHeader) {
        this.callbackHeader = (callbackHeader == null || callbackHeader.isBlank()) ? "X-ESL-Token" : callbackHeader.trim();
    }

    public String fullLightUrl(boolean moving) {
        String path = moving ? lightMovingPath : lightPath;
        return serverUrl + (serverUrl.endsWith("/") ? path.substring(1) : path);
    }

    public String fullQueryUrl() {
        return serverUrl + (serverUrl.endsWith("/") ? queryDeviceInfoPath.substring(1) : queryDeviceInfoPath);
    }
}
