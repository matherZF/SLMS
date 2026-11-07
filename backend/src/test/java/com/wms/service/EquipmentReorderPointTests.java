package com.wms.service;

import com.wms.entity.EquipmentPart;
import com.wms.entity.Product;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 设备备件补货点计算。
 *
 * <p>回归点一：原实现 {@code cycle<=0} 时返回 {@code safetyStock + safetyStock}，
 * 安全库存被翻了一倍，没填更换周期的备件会提前误报缺货。</p>
 *
 * <p>回归点二：安全库存与采购周期已从设备关联上收到备件档案。设备侧只留装机数量与更换周期，
 * 同一备件的共享库存不会被多台设备重复计数。</p>
 */
class EquipmentReorderPointTests {

    private static Product product(int safety, int lead) {
        Product p = new Product();
        p.setSafetyStock(safety);
        p.setProcurementLeadDays(lead);
        return p;
    }

    private static EquipmentPart part(int installed, Integer change) {
        EquipmentPart p = new EquipmentPart();
        p.setInstalledQty(installed);
        p.setChangeCycleDays(change);
        return p;
    }

    @Test
    void 更换周期和采购周期都没填时只按安全库存() {
        assertEquals(120, EquipmentService.reorderPoint(part(20, null), product(120, 0)));
    }

    @Test
    void 更换周期为0时不翻倍() {
        assertEquals(120, EquipmentService.reorderPoint(part(20, 0), product(120, 30)));
    }

    @Test
    void 采购周期为0时不计预估消耗() {
        // 原实现 ceil(0/10)=0 → max(1,0)=1 → 额外加了一份单机数量
        assertEquals(50, EquipmentService.reorderPoint(part(20, 10), product(50, 0)));
    }

    @Test
    void 正常情况按采购周期内的更换次数累加() {
        // 采购 30 天 / 更换 10 天 = 3 次，单机 10 个 → 安全库存 10 + 30
        assertEquals(40, EquipmentService.reorderPoint(part(10, 10), product(10, 30)));
    }

    @Test
    void 采购周期短于更换周期时至少算一次() {
        // ceil(5/60)=1 → 安全库存 5 + 单机 8
        assertEquals(13, EquipmentService.reorderPoint(part(8, 60), product(5, 5)));
    }

    @Test
    void 同一备件装在多台设备时安全库存只算一次() {
        // 同一个备件档案（安全库存 10、采购周期 30），两台设备各自只贡献换件节奏，
        // 不会出现 10+10 的重复计数；差异只来自更换周期与装机数量。
        Product shared = product(10, 30);
        assertEquals(40, EquipmentService.reorderPoint(part(10, 10), shared));
        // 更换 30 天、采购 30 天 → 只换 1 次，装 5 个 → 10 + 5
        assertEquals(15, EquipmentService.reorderPoint(part(5, 30), shared));
    }

    @Test
    void 备件没填安全库存时补货点也不为负() {
        assertEquals(0, EquipmentService.reorderPoint(part(10, 10), product(-5, 0)));
    }
}
