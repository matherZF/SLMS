package com.wms.dto.esl;

/**
 * 单独点灯接口结果回调报文（ctrlShelfIndicatorResult）。
 * 墨水屏服务器作为调用方 POST 到本系统。
 */
public class EslLightLedResultCallbackDTO {

    /** 标签 MAC 地址 */
    private String mac;

    /** true: 点亮成功；false: 点亮失败 */
    private Boolean result;

    /** 标签剩余电量 0~100 */
    private Integer power;

    /** 亮灯编号 */
    private Integer lednum;

    /** 调用请求传入的 cmdtoken，原样返回 */
    private String cmdtoken;

    /** 结果消息：数据成功 / 数据失败 / 数据发送超时 / 唤醒失败 / 基站不在线 / 数据异常 / 数据格式不正确，解析失败 / 参数格式不正确或参数不全，无法执行 / 标签不存在 */
    private String message;

    public String getMac() { return mac; }
    public void setMac(String mac) { this.mac = mac; }
    public Boolean getResult() { return result; }
    public void setResult(Boolean result) { this.result = result; }
    public Integer getPower() { return power; }
    public void setPower(Integer power) { this.power = power; }
    public Integer getLednum() { return lednum; }
    public void setLednum(Integer lednum) { this.lednum = lednum; }
    public String getCmdtoken() { return cmdtoken; }
    public void setCmdtoken(String cmdtoken) { this.cmdtoken = cmdtoken; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
