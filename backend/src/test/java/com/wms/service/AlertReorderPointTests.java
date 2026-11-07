package com.wms.service;

import com.wms.entity.Equipment;
import com.wms.entity.EquipmentPart;
import com.wms.entity.Product;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 备件级补货点：安全库存 + 各设备采购周期内的换件需求之和。
 *
 * <p>回归点：原实现按「单台设备」算补货点，通用件挂在多台设备上会严重低估——
 * 一个装在 10 台设备上的备件，采购周期内实际要 20 个，却只按 1 台的 2 个算。</p>
 */
class AlertReorderPointTests {

    private static EquipmentPart link(String code, int installed, Integer cycle) {
        Equipment e = new Equipment();
        e.setCode(code);
        EquipmentPart p = new EquipmentPart();
        p.setEquipment(e);
        p.setInstalledQty(installed);
        p.setChangeCycleDays(cycle);
        return p;
    }

    @Test
    void 单台设备按采购周期内的更换次数算() {
        // ceil(10/10)=1 次 × 装机 10
        assertEquals(10, AlertService.leadDemand(List.of(link("E1", 10, 10)), 10));
    }

    @Test
    void 同备件装在多台设备时需求要累加() {
        // 10 台 × 2 个，周期 30 天，采购 15 天：每台 ceil(15/30)=1 次 × 2 个 = 20
        List<EquipmentPart> links = List.of(
                link("E1", 2, 30), link("E2", 2, 30), link("E3", 2, 30), link("E4", 2, 30), link("E5", 2, 30),
                link("E6", 2, 30), link("E7", 2, 30), link("E8", 2, 30), link("E9", 2, 30), link("E10", 2, 30));
        assertEquals(20, AlertService.leadDemand(links, 15));
    }

    @Test
    void 更换周期没填的设备不计入需求() {
        assertEquals(0, AlertService.leadDemand(List.of(link("E1", 5, null), link("E2", 5, 0)), 15));
    }

    @Test
    void 采购周期为0时不计需求() {
        assertEquals(0, AlertService.leadDemand(List.of(link("E1", 5, 10)), 0));
    }

    @Test
    void 采购周期长于更换周期时按多次换() {
        // ceil(45/30)=2 次 × 装机 3 = 6
        assertEquals(6, AlertService.leadDemand(List.of(link("E1", 3, 30)), 45));
    }

    @Test
    void 备件级补货点是安全库存加上所有设备的需求() {
        Product product = new Product();
        product.setSafetyStock(5);
        product.setProcurementLeadDays(15);
        // 安全库存 5 + 需求 20 = 25
        List<EquipmentPart> links = List.of(link("E1", 2, 30), link("E2", 2, 30), link("E3", 2, 30),
                link("E4", 2, 30), link("E5", 2, 30), link("E6", 2, 30), link("E7", 2, 30),
                link("E8", 2, 30), link("E9", 2, 30), link("E10", 2, 30));
        assertEquals(25, Math.max(product.getSafetyStock(), 0) + AlertService.leadDemand(links, 15));
    }
}
