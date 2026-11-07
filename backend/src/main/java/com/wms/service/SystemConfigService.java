package com.wms.service;

import com.wms.common.BizException;
import com.wms.dto.SystemConfigDTO;
import com.wms.entity.SystemConfig;
import com.wms.repository.SystemConfigRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 系统配置：备件与预警的可调参数。
 *
 * <p>读取顺序：数据库里的当前值 → 环境变量/yml 里的出厂默认值。
 * 所以配置项被误删、或表还没建好的时候，系统仍然按默认值跑，不会启动失败。</p>
 *
 * <p>这里每天都只被预警定时任务和预测查询调用几十次，配置行数是个位数，
 * 直接查库即可，不需要缓存——加了缓存反而会让「改完不生效」成为一类新故障。</p>
 */
@Service
public class SystemConfigService {
    /** 备件与预警分组 */
    public static final String GROUP_SPARE_PART = "SPARE_PART";

    /**
     * 寿命预警提前天数：预计更换日临近这么多天就提醒更换。
     *
     * <p>台账状态（{@code NEAR_DUE}/{@code DUE}）与更换预警用的是同一个值；
     * 备件档案上的「寿命预警提前(天)」填 0 表示跟随这里，非 0 才是该备件单独设的。</p>
     */
    public static final String K_LIFE_WARNING_DAYS = "spare-part.life.warning-days";
    public static final String K_EXPIRY_AHEAD_DAYS = "spare-part.alert.expiry-ahead-days";
    public static final String K_SAFETY_DAYS = "spare-part.forecast.safety-days";
    public static final String K_DEFAULT_LEAD_DAYS = "spare-part.forecast.default-lead-days";
    public static final String K_PRODUCTION_WINDOW_DAYS = "spare-part.forecast.production-window-days";
    public static final String K_FORECAST_MIN_DAYS = "spare-part.usage.forecast-min-days";

    private final SystemConfigRepository repo;
    private final OperationAuditService auditService;
    private final int lifeWarningDays;
    private final int expiryAheadDays;
    private final int safetyDays;
    private final int defaultLeadDays;
    private final int productionWindowDays;
    private final int forecastMinDays;

    public SystemConfigService(SystemConfigRepository repo,
                               OperationAuditService auditService,
                               @Value("${spare-part.life.warning-days:3}") int lifeWarningDays,
                               @Value("${spare-part.alert.expiry-ahead-days:30}") int expiryAheadDays,
                               @Value("${spare-part.forecast.safety-days:7}") int safetyDays,
                               @Value("${spare-part.forecast.default-lead-days:7}") int defaultLeadDays,
                               @Value("${spare-part.forecast.production-window-days:7}") int productionWindowDays,
                               @Value("${spare-part.usage.forecast-min-days:3}") int forecastMinDays) {
        this.repo = repo;
        this.auditService = auditService;
        this.lifeWarningDays = lifeWarningDays;
        this.expiryAheadDays = expiryAheadDays;
        this.safetyDays = safetyDays;
        this.defaultLeadDays = defaultLeadDays;
        this.productionWindowDays = productionWindowDays;
        this.forecastMinDays = forecastMinDays;
    }

    /**
     * 寿命预警提前天数。台账状态与更换预警共用这一个值：
     * 备件档案上单独设了就用备件档案的，没设（0）才用这里。
     */
    public int lifeWarningDays() { return intValue(K_LIFE_WARNING_DAYS, lifeWarningDays); }
    /** 效期预警提前量：批次到期前这么多天提醒优先出库 */
    public int expiryAheadDays() { return intValue(K_EXPIRY_AHEAD_DAYS, expiryAheadDays); }
    /** 预测时除采购周期外再多留几天的用量 */
    public int safetyDays() { return intValue(K_SAFETY_DAYS, safetyDays); }
    /** 备件没填采购周期时按这个天数估算交期 */
    public int defaultLeadDays() { return intValue(K_DEFAULT_LEAD_DAYS, defaultLeadDays); }
    /** 算设备日均产量时取最近多少天 */
    public int productionWindowDays() { return intValue(K_PRODUCTION_WINDOW_DAYS, productionWindowDays); }
    /** 预测「下次更换日」所需的最短快照跨度 */
    public int forecastMinDays() { return intValue(K_FORECAST_MIN_DAYS, forecastMinDays); }

    /**
     * 读一个整数配置。
     *
     * @param fallback 表里没有该键、或值非法时用的出厂默认值
     */
    public int intValue(String key, int fallback) {
        Optional<SystemConfig> row = repo.findByConfigKey(key);
        if (row.isEmpty() || !row.get().isEnabled()) return fallback;
        try {
            return Integer.parseInt(row.get().getConfigValue().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public List<SystemConfigDTO> list() {
        List<SystemConfigDTO> rows = new ArrayList<>();
        for (SystemConfig c : repo.findAllEnabledOrdered()) rows.add(SystemConfigDTO.of(c));
        return rows;
    }

    @Transactional
    public SystemConfigDTO update(Long id, String value) {
        SystemConfig c = repo.findById(id).orElseThrow(() -> new BizException("配置项不存在"));
        String oldValue = c.getConfigValue();
        c.setConfigValue(validate(c, value));
        repo.save(c);
        auditService.record("SYSTEM_CONFIG", c.getId(), c.getConfigKey(), "UPDATE", oldValue, c.getConfigValue(),
                "修改系统配置 [" + c.getLabel() + "]：" + oldValue + " → " + c.getConfigValue());
        return SystemConfigDTO.of(c);
    }

    /** 恢复出厂默认值 */
    @Transactional
    public SystemConfigDTO reset(Long id) {
        SystemConfig c = repo.findById(id).orElseThrow(() -> new BizException("配置项不存在"));
        String oldValue = c.getConfigValue();
        c.setConfigValue(c.getDefaultValue());
        repo.save(c);
        auditService.record("SYSTEM_CONFIG", c.getId(), c.getConfigKey(), "RESET", oldValue, c.getDefaultValue(),
                "恢复配置 [" + c.getLabel() + "] 为出厂默认值：" + oldValue + " → " + c.getDefaultValue());
        return SystemConfigDTO.of(c);
    }

    private String validate(SystemConfig c, String raw) {
        String value = raw == null ? "" : raw.trim();
        if ("INT".equalsIgnoreCase(c.getValueType())) {
            int v;
            try {
                v = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new BizException(c.getLabel() + " 必须是整数");
            }
            int min = minOf(c.getConfigKey());
            int max = maxOf(c.getConfigKey());
            if (v < min || v > max) {
                throw new BizException(c.getLabel() + " 需在 " + min + " 到 " + max + " 之间");
            }
            return String.valueOf(v);
        }
        if ("BOOL".equalsIgnoreCase(c.getValueType()) && !"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new BizException(c.getLabel() + " 只能是 true 或 false");
        }
        return value;
    }

    private static int minOf(String key) {
        // 全都是「天数」类的正数配置，最小 1 天；取样窗口允许 1 表示只要有两天数据就预测
        return 1;
    }

    private static int maxOf(String key) {
        return switch (key) {
            case K_PRODUCTION_WINDOW_DAYS, K_EXPIRY_AHEAD_DAYS -> 365;
            default -> 180;
        };
    }

    /**
     * 启动时补齐缺失的配置项。已有记录不动（运营可能改过值），只补没有的键。
     *
     * @return 本次新增的条数
     */
    @Transactional
    public int seed() {
        int added = 0;
        added += seed(K_LIFE_WARNING_DAYS, "寿命预警提前天数", lifeWarningDays, 10,
                "预计更换日临近这么多天就提醒更换；备件档案上的「寿命预警提前(天)」填 0 时用这个值");
        added += seed(K_EXPIRY_AHEAD_DAYS, "效期预警提前量", expiryAheadDays, 30, "批次到期前这么多天提醒优先出库");
        added += seed(K_SAFETY_DAYS, "预测安全天数", safetyDays, 40, "除采购周期外再多留几天的用量，保证到货前不见底");
        added += seed(K_DEFAULT_LEAD_DAYS, "默认采购周期", defaultLeadDays, 50, "备件没填采购周期时按这个天数估算交期，页面会标明是估算值");
        added += seed(K_PRODUCTION_WINDOW_DAYS, "日均产量取样天数", productionWindowDays, 60, "用最近多少天的设备产量算日均；太短会被单日异常放大，太长跟不上产线调整");
        added += seed(K_FORECAST_MIN_DAYS, "预测最短样本天数", forecastMinDays, 70, "快照跨度不足时不输出速率与更换日期，避免短窗口里的停机/检修把日均算歪");
        return added;
    }

    private int seed(String key, String label, int defaultValue, int sortOrder, String remark) {
        if (repo.findByConfigKey(key).isPresent()) return 0;
        SystemConfig c = new SystemConfig();
        c.setConfigGroup(GROUP_SPARE_PART);
        c.setConfigKey(key);
        c.setLabel(label);
        c.setConfigValue(String.valueOf(defaultValue));
        c.setDefaultValue(String.valueOf(defaultValue));
        c.setValueType("INT");
        c.setUnit("天");
        c.setSortOrder(sortOrder);
        c.setRemark(remark);
        c.setEnabled(true);
        repo.save(c);
        return 1;
    }
}
