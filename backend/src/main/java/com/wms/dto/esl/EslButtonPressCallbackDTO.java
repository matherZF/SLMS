package com.wms.dto.esl;

/**
 * 电子标签按键事件回调报文（接口文档 enterKey）。
 * <p>
 * 墨水屏服务器 POST 到 WMS，触发后 WMS 会在对应出库项上标记 button_pressed=true，
 * 作为 completePicking() 的必要条件。
 */
public class EslButtonPressCallbackDTO {

    /** 触发按键的标签 MAC；如果是 RFID 读卡器，则为触发的 RFID 标签地址 */
    private String mac;

    /**
     * 按键类型：
     * <ul>
     *   <li>0      —— 右下按键</li>
     *   <li>其他值  —— 读卡器，值为读卡器 ID 后两位的 int（例如 99.99.32.66 → 0x0242 = 578）</li>
     * </ul>
     */
    private Integer result;

    public String getMac() { return mac; }
    public void setMac(String mac) { this.mac = mac; }
    public Integer getResult() { return result; }
    public void setResult(Integer result) { this.result = result; }
}
