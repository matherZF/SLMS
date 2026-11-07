package com.wms.dto.esl;

/**
 * 电子标签 LED 颜色编码常量（直接对应接口文档 ledrgb 字段）。
 */
public final class EslLedColor {
    public static final String RED    = "ff0000";
    public static final String GREEN  = "ff00";
    public static final String BLUE   = "ff";
    public static final String YELLOW = "ffff00";
    public static final String WHITE  = "ffffff";
    public static final String PURPLE = "ff00ff";
    public static final String LIGHT_BLUE = "ffff";
    /** 不亮灯 */
    public static final String OFF    = "0";

    private EslLedColor() {}
}
