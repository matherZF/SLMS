package com.wms.dto.esl;

import java.util.List;

/**
 * WMS 对外暴露的「批量点灯」请求体。
 * 内部会拆分成 {@link EslLightLedItemDTO} 数组按接口文档原样转发。
 */
public class EslLightLedBatchReq {

    /** 是否走移动场景专用接口 /lightMovingTagsLed/v2 */
    private Boolean moving = false;

    /** 点灯明细 */
    private List<EslLightLedItemDTO> items;

    public Boolean getMoving() { return moving; }
    public void setMoving(Boolean moving) { this.moving = moving; }
    public List<EslLightLedItemDTO> getItems() { return items; }
    public void setItems(List<EslLightLedItemDTO> items) { this.items = items; }
}
