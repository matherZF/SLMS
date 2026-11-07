package com.wms;

import com.wms.service.InterfaceLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class InterfaceLogTests {

    @Autowired
    private InterfaceLogService service;

    @Test
    void inboundPayloadIsStoredAndQueryable() {
        service.recordInbound("DPS_TRIGGER", "定位指引触发", "HTTP", "127.0.0.1",
                "{\"guideCode\":\"A-01-01\"}", "{\"received\":true}", true, null, "trace-001", 15L);

        var page = service.page("INBOUND", "DPS_TRIGGER", true, "dps", 0, 10);
        assertTrue(page.getTotalElements() >= 1);
        var summary = page.getContent().get(0);
        assertEquals("DPS_TRIGGER", summary.interfaceCode());
        assertTrue(summary.success());

        var detail = service.detail(summary.id());
        assertEquals("{\"guideCode\":\"A-01-01\"}", detail.requestBody());
    }

    @Test
    void outboundFailureKeepsErrorMessage() {
        service.recordOutbound("DEVICE_RUNTIME_QUERY", "设备运行数据查询", "JDBC", "设备运行数据库",
                "select 1", null, false, "连接超时", null, 3000L);

        var page = service.page("OUTBOUND", "DEVICE_RUNTIME_QUERY", false, null, 0, 10);
        assertTrue(page.getTotalElements() >= 1);
        assertEquals("连接超时", page.getContent().get(0).errorMessage());
    }
}
