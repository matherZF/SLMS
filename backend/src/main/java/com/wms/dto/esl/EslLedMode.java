package com.wms.dto.esl;

/**
 * 电子标签 LED 灯模式常量（对应接口文档 ledmode 字段）。
 */
public final class EslLedMode {
    /** 常亮 */
    public static final int ON      = 0;
    /** 快闪 */
    public static final int FAST_BLINK = 1;
    /** 慢闪 */
    public static final int SLOW_BLINK = 2;
    /** 灭灯 */
    public static final int OFF     = 3;
    /** 全灭（所有灯） */
    public static final int ALL_OFF = 4;

    private EslLedMode() {}
}
