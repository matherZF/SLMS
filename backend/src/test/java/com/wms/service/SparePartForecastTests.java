package com.wms.service;

import com.wms.dto.SparePartForecastVO;
import com.wms.entity.Equipment;
import com.wms.entity.EquipmentPart;
import com.wms.entity.Product;
import org.junit.jupiter.api.Test;
import com.wms.service.SparePartForecastService.DeviceOutput;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 备件需求预测算法。
 *
 * <p>这些断言对应的都是现场会出事的判断：需求算小了 → 备件不够 → 停机等件；
 * 需求算大了 → 呆滞库存。所以口径、取大、缺参数不猜这几条都必须钉死。</p>
 */
class SparePartForecastTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);

    /* ---------- 三个口径 ---------- */

    @Test
    void 件数口径按产量除以额定处理件数() {
        // 装 2 个，设备每天处理 1000 件，每个能处理 10000 件 → 每天磨掉 0.2 个
        BigDecimal daily = SparePartForecastService.qtyDemand(2, new BigDecimal("1000"), new BigDecimal("10000"));
        assertThat(daily).isEqualByComparingTo("0.2");
    }

    @Test
    void 周期口径按装机数除以天数() {
        assertThat(SparePartForecastService.cycleDemand(1, 10)).isEqualByComparingTo("0.1");
        assertThat(SparePartForecastService.cycleDemand(20, 30)).isEqualByComparingTo("0.666667");
    }

    @Test
    void 五口径取最大谁先到期按谁补() {
        // 件数口径 2×1000/100000=0.02；运行 2×10/1000=0.02；任务 2×20/100=0.4
        // 周期口径 2/10=0.2；天数口径 2/30=0.0667 → 取任务的 0.4
        BigDecimal daily = SparePartForecastService.daily(2,
                new SparePartForecastService.DeviceOutput(new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("1000")),
                new BigDecimal("1000"), new BigDecimal("100"), new BigDecimal("100000"), 10, new BigDecimal("30"));
        assertThat(daily).isEqualByComparingTo("0.4");
    }

    /** 源表只给时长不给产量时，HOURS 口径照样能算，不能因为少一个字段就判不可预测 */
    @Test
    void 设备只上报运行时长时按时长口径算() {
        SparePartForecastService.DeviceOutput out =
                new SparePartForecastService.DeviceOutput(new BigDecimal("8"), null, null);
        assertThat(SparePartForecastService.daily(4, out, new BigDecimal("800"), null, null, null, null))
                .isEqualByComparingTo("0.04");   // 4 台 × 8 小时 ÷ 800 小时
        // 没配额定运行小时，时长数据就失业了
        assertThat(SparePartForecastService.daily(4, out, null, null, null, null, null))
                .isEqualByComparingTo("0");
    }

    @Test
    void 设备只上报任务次数时按任务口径算() {
        SparePartForecastService.DeviceOutput out =
                new SparePartForecastService.DeviceOutput(null, new BigDecimal("30"), null);
        assertThat(SparePartForecastService.daily(2, out, null, new BigDecimal("600"), null, null, null))
                .isEqualByComparingTo("0.1");    // 2 × 30 ÷ 600
    }

    @Test
    void 没有任何寿命参数时不算需求() {
        assertThat(SparePartForecastService.daily(2, DeviceOutput.EMPTY, null, null, null, null, null))
                .isEqualByComparingTo("0");
        // 只配了件数口径但设备没产量，也不能拿 0 当有效值
        assertThat(SparePartForecastService.daily(2, DeviceOutput.EMPTY, null, null, new BigDecimal("100000"), null, null))
                .isEqualByComparingTo("0");
    }

    /* ---------- 覆盖天数与补货量 ---------- */

    @Test
    void 覆盖天数等于库存除以日耗且向下取整() {
        assertThat(SparePartForecastService.coverageDays(90, new BigDecimal("1"))).isEqualTo(90);
        assertThat(SparePartForecastService.coverageDays(10, new BigDecimal("0.67"))).isEqualTo(14);
        assertThat(SparePartForecastService.coverageDays(0, new BigDecimal("1"))).isEqualTo(0);
        assertThat(SparePartForecastService.coverageDays(10, BigDecimal.ZERO)).isNull();
    }

    @Test
    void 建议补货量补到撑过采购周期加安全天数() {
        // 日耗 2，采购 10 天，安全 7 天，库存 5 → 目标 34，需补 29
        assertThat(SparePartForecastService.suggestQty(new BigDecimal("2"), 5, 0, 10, 7, 7))
                .isEqualTo(29);
        // 库存已经超过目标，不用补
        assertThat(SparePartForecastService.suggestQty(new BigDecimal("2"), 100, 0, 10, 7, 7))
                .isEqualTo(0);
    }

    /** 缺采购周期时按默认值估，不能因为参数缺失就把补货量算成 0 */
    @Test
    void 缺采购周期时按默认天数估算补货量() {
        // 日耗 1，默认 7 天 + 安全 7 天 = 14，库存 5 → 至少补 9
        assertThat(SparePartForecastService.suggestQty(new BigDecimal("1"), 5, 0, 0, 7, 7))
                .isEqualTo(9);
    }

    /* ---------- 严重度 ---------- */

    @Test
    void 已过下单日或已断货为高级别() {
        String high = SparePartForecastService.severityOf(
                TODAY, 0, TODAY, TODAY, 10, 7, 7);
        assertThat(high).isEqualTo("HIGH");

        // 覆盖 5 天、采购 10 天 → 下单日已过
        String late = SparePartForecastService.severityOf(
                TODAY, 5, TODAY.plusDays(5), TODAY.minusDays(5), 10, 7, 7);
        assertThat(late).isEqualTo("HIGH");
    }

    @Test
    void 撑不过采购周期加安全天数为中级别() {
        // 覆盖 15 天，采购 10 + 安全 7 = 17 → 中级别
        assertThat(SparePartForecastService.severityOf(
                TODAY, 15, TODAY.plusDays(15), TODAY.plusDays(5), 10, 7, 7)).isEqualTo("MEDIUM");
        // 覆盖 60 天，远大于 17 → 不告警
        assertThat(SparePartForecastService.severityOf(
                TODAY, 60, TODAY.plusDays(60), TODAY.plusDays(50), 10, 7, 7)).isNull();
    }

    /** 缺少采购周期不能等同于"不用补货"：照样要预警，采购日按默认交期估，但要标明是估算 */
    @Test
    void 缺采购周期时仍要预警并给出到货期限与数量() {
        Product p = product("DAYS", "[{\"metric\":\"DAYS\",\"rated\":10}]", 0, 0);
        EquipmentPart link = link("SC01", 10, 10);   // 日耗 1，库存 5 → 覆盖 5 天

        SparePartForecastVO vo = SparePartForecastService.build(
                p, List.of(link), Map.of("SC01", DeviceOutput.EMPTY), 5, TODAY, 7, 7, 7);

        assertThat(vo.status()).isEqualTo("READY");
        assertThat(vo.leadEstimated()).isTrue();
        assertThat(vo.leadDays()).isNull();
        assertThat(vo.severity()).isEqualTo("HIGH");
        // 到货期限与最少数量必须给得出来，这是这条预警的全部价值
        assertThat(vo.receiveByDate()).isEqualTo(TODAY.minusDays(2));     // 缺货 T+5 − 安全 7 天
        assertThat(vo.stockoutDate()).isEqualTo(TODAY.plusDays(5));
        assertThat(vo.suggestedQty()).isEqualTo(9);                        // 1×(7+7) − 5
        assertThat(vo.actionHint()).contains("立即到货").contains("9 件");
        assertThat(vo.gaps()).anyMatch(g -> g.contains("采购周期"));
    }

    @Test
    void 有采购周期时同时给出采购日与到货日() {
        Product p = product("DAYS", "[{\"metric\":\"DAYS\",\"rated\":10}]", 10, 0);
        EquipmentPart link = link("SC01", 1, null);   // 日耗 0.1，库存 1 → 覆盖 10 天

        SparePartForecastVO vo = SparePartForecastService.build(
                p, List.of(link), Map.of("SC01", DeviceOutput.EMPTY), 1, TODAY, 7, 7, 7);

        assertThat(vo.leadEstimated()).isFalse();
        assertThat(vo.coverageDays()).isEqualTo(10);
        assertThat(vo.stockoutDate()).isEqualTo(TODAY.plusDays(10));
        assertThat(vo.receiveByDate()).isEqualTo(TODAY.plusDays(3));    // 缺货日 − 安全 7 天
        assertThat(vo.orderByDate()).isEqualTo(TODAY.minusDays(7));     // 到货日 − 采购 10 天
        assertThat(vo.suggestedQty()).isEqualTo(1);                     // ⌈0.1×(10+7)⌉ − 1
        assertThat(vo.actionHint()).contains("前完成采购").contains("前到货").contains("最少 1 件");
    }

    @Test
    void 行动建议在到货日已逾期时要求立刻到货() {
        // 日耗 1，库存 5 → 覆盖 5 天，最迟到货日 T-2 已经是过去
        String hint = SparePartForecastService.actionHint(
                TODAY, 5, TODAY.minusDays(2), TODAY.minusDays(12), 9, false, 7, "件");
        assertThat(hint).contains("已过").contains("立即到货");
    }

    @Test
    void 库存充足时行动建议说明暂不需补货() {
        String hint = SparePartForecastService.actionHint(
                TODAY, 200, TODAY.plusDays(193), TODAY.plusDays(183), 0, false, 7, "件");
        assertThat(hint).contains("暂不需补货");
    }

    /* ---------- 端到端组装 ---------- */

    @Test
    void 正常备件能给出下单日与补货量() {
        Product p = product("DAYS", "[{\"metric\":\"DAYS\",\"rated\":10}]", 10, 0);
        EquipmentPart link = link("SC01", 10, 10);

        SparePartForecastVO vo = SparePartForecastService.build(
                p, List.of(link), Map.of("SC01", DeviceOutput.EMPTY), 5, TODAY, 7, 7, 7);

        assertThat(vo.status()).isEqualTo("READY");
        assertThat(vo.dailyDemand()).isEqualByComparingTo("1.00");
        assertThat(vo.coverageDays()).isEqualTo(5);
        assertThat(vo.stockoutDate()).isEqualTo(TODAY.plusDays(5));
        assertThat(vo.receiveByDate()).isEqualTo(TODAY.minusDays(2));
        assertThat(vo.orderByDate()).isEqualTo(TODAY.minusDays(12));
        assertThat(vo.suggestedQty()).isEqualTo(12);       // 1×(10+7) − 5
        assertThat(vo.severity()).isEqualTo("HIGH");
    }

    @Test
    void 没关联设备时明确说缺什么() {
        Product p = product("DAYS", "[{\"metric\":\"DAYS\",\"rated\":30}]", 0, 0);

        SparePartForecastVO vo = SparePartForecastService.build(
                p, List.of(), Map.of(), 80, TODAY, 7, 7, 7);

        assertThat(vo.status()).isEqualTo("NO_DEVICE");
        assertThat(vo.dailyDemand()).isNull();
        assertThat(vo.gaps()).anyMatch(g -> g.contains("设备档案"));
    }

    @Test
    void 只有件数口径而设备没产量时说设备未在产() {
        Product p = product("QTY", "[{\"metric\":\"QTY\",\"rated\":100000}]", 0, 0);
        EquipmentPart link = link("MD3", 20, null);

        SparePartForecastVO vo = SparePartForecastService.build(
                p, List.of(link), Map.of(), 80, TODAY, 7, 7, 7);

        assertThat(vo.status()).isEqualTo("NO_PRODUCTION");
        assertThat(vo.dailyDemand()).isNull();
    }

    /** 产量参与计算时，依据里必须写出来，否则现场无法核对这个数是怎么来的 */
    @Test
    void 件数口径生效时依据写明产量与寿命() {
        Product p = product("QTY", "[{\"metric\":\"QTY\",\"rated\":10000}]", 0, 0);
        EquipmentPart link = link("MD3", 2, null);

        SparePartForecastVO vo = SparePartForecastService.build(
                p, List.of(link), Map.of("MD3", new DeviceOutput(null, null, new BigDecimal("1000"))),
                100, TODAY, 7, 7, 7);

        assertThat(vo.dailyDemand()).isEqualByComparingTo("0.20");
        assertThat(vo.devices()).hasSize(1);
        assertThat(vo.devices().get(0).basis()).contains("日均产出 1000 件").contains("10000 件");
    }

    /** 有产量但没配件数口径，产量用不上，必须提示，否则会误以为已经在按产量算了 */
    @Test
    void 有产量却未配件数口径时给出提示() {
        Product p = product("DAYS", "[{\"metric\":\"DAYS\",\"rated\":30}]", 0, 0);
        EquipmentPart link = link("MD3", 20, null);

        SparePartForecastVO vo = SparePartForecastService.build(
                p, List.of(link), Map.of("MD3", new DeviceOutput(null, null, new BigDecimal("74"))),
                80, TODAY, 7, 7, 7);

        assertThat(vo.devices().get(0).basis()).contains("处理件数").contains("未参与");
    }

    /** 设备报了运行时长但备件没配 HOURS 口径，同样要提示——时长往往比件数更能反映磨损 */
    @Test
    void 有运行时长却未配时长口径时给出提示() {
        Product p = product("DAYS", "[{\"metric\":\"DAYS\",\"rated\":30}]", 0, 0);
        EquipmentPart link = link("MD3", 1, null);

        SparePartForecastVO vo = SparePartForecastService.build(
                p, List.of(link), Map.of("MD3", new DeviceOutput(new BigDecimal("12"), null, null)),
                80, TODAY, 7, 7, 7);

        assertThat(vo.devices().get(0).basis()).contains("运行时长").contains("未参与");
    }

    /* ---------- 构造 ---------- */

    private Product product(String metric, String rulesJson, int leadDays, int safetyStock) {
        Product p = new Product();
        p.setSku("F-SKU");
        p.setName("预测测试备件");
        p.setUnit("件");
        p.setLifeMetric(metric);
        p.setLifeMetricRules(rulesJson);
        p.setRatedLifeValue(new BigDecimal("10"));
        p.setProcurementLeadDays(leadDays);
        p.setSafetyStock(safetyStock);
        return p;
    }

    private EquipmentPart link(String code, int installedQty, Integer cycleDays) {
        Equipment e = new Equipment();
        e.setCode(code);
        e.setName(code);
        EquipmentPart link = new EquipmentPart();
        link.setEquipment(e);
        link.setInstalledQty(installedQty);
        link.setChangeCycleDays(cycleDays);
        return link;
    }
}
