package com.wms.service;

import com.wms.dto.SparePartUsageVO;
import com.wms.entity.*;
import com.wms.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 出库建台账时「使用设备」的绑定规则：
 * 由基础数据的设备-备件关联决定，出库单不再指定设备。
 */
@SpringBootTest
@Transactional
class SparePartUsageBindingTests {

    @Autowired private SparePartUsageService service;
    @Autowired private ProductRepository productRepo;
    @Autowired private EquipmentRepository equipmentRepo;
    @Autowired private EquipmentPartRepository partRepo;
    @Autowired private OutboundOrderRepository orderRepo;
    @Autowired private OutboundItemRepository itemRepo;
    @Autowired private SparePartUsageRepository usageRepo;

    @Test
    void bindsTheOnlyInstalledDevice() {
        Product p = product("DAYS+QTY", "[{\"metric\":\"QTY\",\"rated\":100000}]");
        Equipment e = equipment("BIND-5201");
        bind(e, p, 4);

        SparePartUsage u = outbound(p, "BIND-1");

        assertThat(u.getEquipmentId()).isEqualTo(e.getId());
        assertThat(u.getEquipmentCode()).isEqualTo("BIND-5201");
        assertThat(u.getEquipmentName()).isEqualTo("BIND-5201");
        assertThat(u.getBaselineStatus()).isEqualTo("READY");
    }

    @Test
    void staysUnboundWhenProductHasNoEquipmentLink() {
        Product p = product(null, "[{\"metric\":\"QTY\",\"rated\":100000}]");

        SparePartUsage u = outbound(p, "BIND-2");

        assertThat(u.getEquipmentId()).isNull();
        assertThat(u.getStatus()).isEqualTo("BASELINE_PENDING");
        assertThat(u.getDataError()).contains("未在基础数据中关联设备");
    }

    @Test
    void staysUnboundWhenProductIsInstalledOnSeveralDevices() {
        Product p = product(null, "[{\"metric\":\"QTY\",\"rated\":100000}]");
        bind(equipment("BIND-MD3"), p, 1);
        bind(equipment("BIND-MD4"), p, 1);

        SparePartUsage u = outbound(p, "BIND-3");

        assertThat(u.getEquipmentId()).isNull();
        assertThat(u.getStatus()).isEqualTo("BASELINE_PENDING");
        assertThat(u.getDataError()).contains("2 台设备").contains("BIND-MD3").contains("BIND-MD4");
    }

    /** 纯自然天数口径不依赖设备，没关联设备也能正常跟踪，不该被置为待补基准 */
    @Test
    void daysOnlyRuleWorksWithoutAnyDevice() {
        Product p = product(null, "[{\"metric\":\"DAYS\",\"rated\":30}]");

        SparePartUsage u = outbound(p, "BIND-4");

        assertThat(u.getEquipmentId()).isNull();
        assertThat(u.getBaselineStatus()).isEqualTo("READY");
        assertThat(u.getStatus()).isEqualTo("IN_USE");
        assertThat(u.getDataError()).isNull();
    }

    /** 关联是建台账之后才补的：纯天数口径不连设备库，刷新仍要按当前基础数据把设备号认回来 */
    @Test
    void picksUpDeviceWhenLinkIsAddedLater() {
        Product p = product(null, "[{\"metric\":\"DAYS\",\"rated\":180}]");
        SparePartUsage u = outbound(p, "BIND-5");
        assertThat(u.getEquipmentId()).isNull();

        Equipment e = equipment("BIND-5201-LATE");
        bind(e, p, 2, 10);
        SparePartUsageVO after = service.refresh(u.getId());

        assertThat(after.equipmentCode()).isEqualTo("BIND-5201-LATE");
        assertThat(after.dataError()).isNull();
        assertThat(after.plannedCycleDays()).isEqualTo(10);
        assertThat(after.plannedEndAt()).isNotNull();
        assertThat(after.expectedReplaceAt()).isEqualTo(after.plannedEndAt());
    }

    /** 刷新不改写已经认定的设备：出库当时装在哪台是既成事实 */
    @Test
    void keepsOriginalDeviceOnRefresh() {
        Product p = product(null, "[{\"metric\":\"DAYS\",\"rated\":180}]");
        Equipment original = equipment("BIND-ORIG-1");
        bind(original, p, 1, 5);
        SparePartUsage u = outbound(p, "BIND-6");

        bind(equipment("BIND-OTHER-2"), p, 1, 90);
        SparePartUsageVO after = service.refresh(u.getId());

        assertThat(after.equipmentCode()).isEqualTo("BIND-ORIG-1");
        assertThat(after.plannedCycleDays()).isEqualTo(5);
    }

    private Product product(String ignore, String rulesJson) {
        Product p = new Product();
        p.setSku("BIND-SKU-" + UUID.randomUUID().toString().substring(0, 8));
        p.setName("绑定规则测试备件");
        p.setUnit("件");
        p.setLifeMetricRules(rulesJson);
        p.setLifeMetric("QTY");
        p.setRatedLifeValue(java.math.BigDecimal.valueOf(100000));
        return productRepo.save(p);
    }

    private Equipment equipment(String code) {
        Equipment e = new Equipment();
        e.setCode(code);
        e.setName(code);
        e.setStatus("RUNNING");
        return equipmentRepo.save(e);
    }

    private void bind(Equipment e, Product p, int qty) {
        bind(e, p, qty, null);
    }

    private void bind(Equipment e, Product p, int qty, Integer changeCycleDays) {
        EquipmentPart ep = new EquipmentPart();
        ep.setEquipment(e);
        ep.setProduct(p);
        ep.setInstalledQty(qty);
        ep.setChangeCycleDays(changeCycleDays);
        partRepo.save(ep);
    }

    private SparePartUsage outbound(Product p, String tag) {
        OutboundOrder order = new OutboundOrder();
        order.setOrderNo(tag + "-" + UUID.randomUUID().toString().substring(0, 8));
        order.setStatus(OutboundStatus.DONE);
        order.setPurpose("绑定规则测试");
        order.setDepartment("测试部");
        order.setRecipient("测试员");
        orderRepo.save(order);

        OutboundItem item = new OutboundItem();
        item.setOutboundOrder(order);
        item.setProduct(p);
        item.setProductSkuSnapshot(p.getSku());
        item.setProductNameSnapshot(p.getName());
        item.setRequestedQty(2);
        item.setPickedQty(2);
        itemRepo.save(item);

        SparePartUsage u = service.createFromOutbound(order, item, Instant.now());
        return usageRepo.findById(u.getId()).orElseThrow();
    }
}
