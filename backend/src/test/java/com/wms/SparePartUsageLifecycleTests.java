package com.wms;

import com.wms.entity.Product;
import com.wms.entity.SparePartUsage;
import com.wms.repository.OperationAuditLogRepository;
import com.wms.repository.ProductRepository;
import com.wms.repository.SparePartUsageRepository;
import com.wms.repository.SparePartUsageSnapshotRepository;
import com.wms.service.AlertService;
import com.wms.service.SparePartUsageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SparePartUsageLifecycleTests {
    @Autowired private SparePartUsageService service;
    @Autowired private SparePartUsageRepository usageRepo;
    @Autowired private SparePartUsageSnapshotRepository snapshotRepo;
    @Autowired private OperationAuditLogRepository auditRepo;
    @Autowired private ProductRepository productRepo;
    @Autowired private AlertService alertService;

    @Test
    void partialAndFullReturnUpdateUsageAndStopTracking() {
        SparePartUsage usage = saveUsage("DAYS", "IN_USE", 5);

        service.applyReturn(usage.getOutboundItemId(), 2, "RT-1", "剩余物料退回");
        SparePartUsage partial = usageRepo.findById(usage.getId()).orElseThrow();
        assertThat(partial.getActiveQty()).isEqualTo(3);
        assertThat(partial.getReturnedQty()).isEqualTo(2);
        assertThat(partial.getStatus()).isEqualTo("IN_USE");

        service.applyReturn(usage.getOutboundItemId(), 3, "RT-2", "全部退回");
        SparePartUsage returned = usageRepo.findById(usage.getId()).orElseThrow();
        assertThat(returned.getActiveQty()).isZero();
        assertThat(returned.getReturnedQty()).isEqualTo(5);
        assertThat(returned.getStatus()).isEqualTo("RETURNED");
        assertThat(returned.getEndedAt()).isNotNull();
        assertThat(usageRepo.findTrackable()).extracting(SparePartUsage::getId).doesNotContain(usage.getId());
        assertThat(snapshotRepo.findTop100ByUsageIdOrderByCollectedAtDesc(usage.getId())).hasSize(2);
    }

    @Test
    void closingTrackingDoesNotBlockLaterPhysicalReturn() {
        SparePartUsage usage = saveUsage("DAYS", "IN_USE", 4);
        service.end(usage.getId(), "停止跟踪");

        service.applyReturn(usage.getOutboundItemId(), 2, "RT-3", "关闭后部分退回");
        SparePartUsage partial = usageRepo.findById(usage.getId()).orElseThrow();
        assertThat(partial.getStatus()).isEqualTo("CLOSED");
        assertThat(partial.getActiveQty()).isZero();
        assertThat(partial.getReturnedQty()).isEqualTo(2);

        service.applyReturn(usage.getOutboundItemId(), 2, "RT-4", "关闭后全部退回");
        SparePartUsage returned = usageRepo.findById(usage.getId()).orElseThrow();
        assertThat(returned.getStatus()).isEqualTo("RETURNED");
        assertThat(returned.getReturnedQty()).isEqualTo(4);
    }

    @Test
    void batchRefreshWritesValidAuditAndKeepsRunningWhenDeviceSourceIsDisabled() {
        SparePartUsage days = saveUsage("DAYS", "IN_USE", 1);
        SparePartUsage hours = saveUsage("HOURS", "BASELINE_PENDING", 1);

        Map<String, Integer> result = service.refreshAll("MANUAL");

        assertThat(result).containsEntry("total", 2).containsEntry("success", 1).containsEntry("failed", 1);
        assertThat(auditRepo.findByBusinessTypeAndBusinessIdOrderByIdAsc("SPARE_USAGE", 0L))
                .anyMatch(log -> "BATCH_REFRESH".equals(log.getAction()));
        assertThat(usageRepo.findById(days.getId()).orElseThrow().getLastSyncedAt()).isNotNull();
        assertThat(usageRepo.findById(hours.getId()).orElseThrow().getDataError()).contains("未启用");
    }

    @Test
    void lifecycleAlertsComeFromNewUsageLedger() {
        Product product = new Product("T-" + shortId(), "测试寿命预警备件", "TEST", "件");
        product.setMinStock(0); product.setSafetyStock(0); product = productRepo.save(product);
        SparePartUsage usage = saveUsage("DAYS", "NEAR_DUE", 2);
        usage.setProductId(product.getId()); usage.setSku(product.getSku()); usage.setProductName(product.getName());
        usageRepo.save(usage);

        List<Map<String, Object>> alerts = alertService.alerts();

        assertThat(alerts).anyMatch(row -> "LIFECYCLE".equals(row.get("alertType"))
                && usage.getId().equals(row.get("usageId")) && Integer.valueOf(2).equals(row.get("activeQty")));
    }

    private SparePartUsage saveUsage(String metric, String status, int qty) {
        String id = shortId();
        SparePartUsage usage = new SparePartUsage();
        usage.setUsageNo("USE-T-" + id); usage.setOutboundOrderId(900000L + Math.abs(id.hashCode()));
        usage.setOutboundOrderNo("OUT-T-" + id); usage.setOutboundItemId(800000L + Math.abs(id.hashCode()));
        usage.setProductId(700000L + Math.abs(id.hashCode())); usage.setSku("SKU-T-" + id);
        usage.setProductName("测试备件"); usage.setUnit("件"); usage.setQty(qty); usage.setActiveQty(qty);
        // 必须给设备编号：HOURS/TASK 口径要先定位设备，没有编号会直接报"未关联设备"，
        // 测不到"设备数据源未启用"这条分支（batchRefresh 用例就是靠这个分支断言 failed=1）
        usage.setEquipmentCode("EQ-T-" + id);
        usage.setReturnedQty(0); usage.setLifeMetric(metric); usage.setRatedLifeValue(BigDecimal.valueOf(100));
        usage.setWarningPercent(80); usage.setBaselineStatus("BASELINE_PENDING".equals(status) ? "PENDING" : "READY");
        usage.setStatus(status); usage.setStartedAt(Instant.now().minusSeconds(90L * 86400));
        return usageRepo.save(usage);
    }

    private String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
