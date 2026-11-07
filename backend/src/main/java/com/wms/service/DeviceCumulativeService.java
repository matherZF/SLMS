package com.wms.service;

import com.wms.repository.DeviceStatDailyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * 设备累计运行量（件数 / 运行时长 / 任务次数）的取数入口。
 *
 * <p>数据来自定时同步落地的本地表 {@code wms_device_stat_daily}（WCS/WMS 每日汇总），
 * 按设备编号累加即为"从同步起始日到昨天"的累计量。三个口径只用于计算"基准 → 当前"的
 * <b>差值</b>，所以同步开始得晚并不影响已用量的准确性。</p>
 *
 * <p><b>无数据一律返回 null，不能返回 0</b>：0 会被当成真实的累计值建立基准，
 * 之后要等很久才会动，属于静默错误。判断标准是"有没有真报过"——
 * 行存在但该列全是 0 或 NULL，说明源系统不提供这个字段（例如部分设备的
 * {@code SUM_HOURS} 恒为 0），同样按无数据处理，由调用方决定退回别的口径还是报缺口。</p>
 */
@Service
public class DeviceCumulativeService {

    private final DeviceStatDailyRepository repo;
    private final String pieceSource;

    public DeviceCumulativeService(DeviceStatDailyRepository repo,
                                   @Value("${spare-part.usage-sync.piece-source:}") String pieceSource) {
        this.repo = repo;
        this.pieceSource = pieceSource;
    }

    /** 设备累计处理件数；无任何日汇总数据时返回 null */
    public Long cumulativeQty(String equipmentCode) {
        if (!StringUtils.hasText(equipmentCode)) return null;
        String code = equipmentCode.trim();
        long rows;
        BigDecimal sum;
        if (StringUtils.hasText(pieceSource)) {
            String source = pieceSource.trim().toUpperCase();
            rows = repo.countByDeviceCodeAndSourceNative(code, source);
            if (rows == 0) return null;
            sum = repo.sumQtyByDeviceCodeAndSourceNative(code, source);
        } else {
            rows = repo.countByDeviceCodeNative(code);
            if (rows == 0) return null;
            sum = repo.sumQtyByDeviceCodeNative(code);
        }
        return sum == null ? 0L : sum.longValue();
    }

    /**
     * 设备累计运行小时；该设备从未上报过运行时长时返回 null。
     *
     * <p>不能因为源表这列全 0 就当作"设备没跑过"——立项素材里明确写了
     * {@code SUM_HOURS} 只有部分设备拿得到，全 0 更可能是字段没采。</p>
     */
    public BigDecimal cumulativeRunningHours(String equipmentCode) {
        if (!StringUtils.hasText(equipmentCode)) return null;
        String code = equipmentCode.trim();
        if (repo.countRunningHoursByDeviceCodeNative(code) == 0) return null;
        return repo.sumRunningHoursByDeviceCodeNative(code);
    }

    /** 设备累计任务次数；从未上报过时返回 null */
    public Long cumulativeTaskCount(String equipmentCode) {
        if (!StringUtils.hasText(equipmentCode)) return null;
        String code = equipmentCode.trim();
        if (repo.countTaskCountByDeviceCodeNative(code) == 0) return null;
        return repo.sumTaskCountByDeviceCodeNative(code);
    }
}
