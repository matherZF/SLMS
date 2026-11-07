package com.wms.dto.esl;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 单独点灯接口 —— 单条标签请求项。
 * 对应接口文档 /wms/associate/lightTagsLed 请求数组中的每个元素。
 */
public class EslLightLedItemDTO {

    /** 标签 MAC 地址（必填） */
    @NotBlank(message = "mac 不能为空")
    private String mac;

    /** 灯号，默认单灯传 0 或 255（可选） */
    private Integer lednum;

    /** 自动熄灭秒数；0 = 不自动熄灭（必填） */
    @NotNull(message = "timeout 不能为空")
    private Integer timeout;

    /** 颜色编码，参考 {@link EslLedColor}（必填） */
    @NotBlank(message = "ledrgb 不能为空")
    private String ledrgb;

    /** 灯模式，参考 {@link EslLedMode}（必填） */
    @NotNull(message = "ledmode 不能为空")
    private Integer ledmode;

    /** 调用方自定义扩展参数，回调会原样带回，用于请求-响应匹配（可选） */
    private String cmdtoken;

    /** 预留字段（可选） */
    private String reserve;

    public EslLightLedItemDTO() {}

    public EslLightLedItemDTO(String mac, Integer timeout, String ledrgb, Integer ledmode) {
        this.mac = mac;
        this.timeout = timeout;
        this.ledrgb = ledrgb;
        this.ledmode = ledmode;
    }

    public String getMac() { return mac; }
    public void setMac(String mac) { this.mac = mac; }
    public Integer getLednum() { return lednum; }
    public void setLednum(Integer lednum) { this.lednum = lednum; }
    public Integer getTimeout() { return timeout; }
    public void setTimeout(Integer timeout) { this.timeout = timeout; }
    public String getLedrgb() { return ledrgb; }
    public void setLedrgb(String ledrgb) { this.ledrgb = ledrgb; }
    public Integer getLedmode() { return ledmode; }
    public void setLedmode(Integer ledmode) { this.ledmode = ledmode; }
    public String getCmdtoken() { return cmdtoken; }
    public void setCmdtoken(String cmdtoken) { this.cmdtoken = cmdtoken; }
    public String getReserve() { return reserve; }
    public void setReserve(String reserve) { this.reserve = reserve; }
}
