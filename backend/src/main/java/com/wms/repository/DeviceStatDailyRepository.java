package com.wms.repository;

import com.wms.entity.DeviceStatDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface DeviceStatDailyRepository extends JpaRepository<DeviceStatDaily, Long> {
    List<DeviceStatDaily> findBySourceSysAndStatDateOrderByDeviceTypeAscDeviceCodeAsc(String sourceSys, LocalDate statDate);

    List<DeviceStatDaily> findByDeviceCodeOrderByStatDateDesc(String deviceCode);

    List<DeviceStatDaily> findByStatDateBetween(LocalDate from, LocalDate to);

    /** 一台设备对多个外部编码（双向托盘线两端站台）时按编码集合取数 */
    List<DeviceStatDaily> findByStatDateBetweenAndDeviceCodeIn(LocalDate from, LocalDate to, Collection<String> deviceCodes);

    /** 按编码集合取全部历史（累计口径用；条数 = 天数 × 编码数 × 源数，量很小） */
    List<DeviceStatDaily> findByDeviceCodeIn(Collection<String> deviceCodes);

    /* ---------- 备件寿命 QTY 口径：按设备累加历史件数 ---------- */

    @Query(value = "select count(*) from wms_device_stat_daily where device_code = :code", nativeQuery = true)
    long countByDeviceCodeNative(@Param("code") String code);

    @Query(value = "select count(*) from wms_device_stat_daily where device_code = :code and source_sys = :source", nativeQuery = true)
    long countByDeviceCodeAndSourceNative(@Param("code") String code, @Param("source") String source);

    @Query(value = "select coalesce(sum(total_qty), 0) from wms_device_stat_daily where device_code = :code", nativeQuery = true)
    BigDecimal sumQtyByDeviceCodeNative(@Param("code") String code);

    @Query(value = "select coalesce(sum(total_qty), 0) from wms_device_stat_daily "
            + "where device_code = :code and source_sys = :source", nativeQuery = true)
    BigDecimal sumQtyByDeviceCodeAndSourceNative(@Param("code") String code, @Param("source") String source);

    /* ---------- 同上，但按编码集合（含外部映射编码，见 DeviceCodeResolver） ---------- */

    @Query(value = "select count(*) from wms_device_stat_daily where device_code in (:codes)", nativeQuery = true)
    long countByDeviceCodesNative(@Param("codes") Collection<String> codes);

    @Query(value = "select count(*) from wms_device_stat_daily "
            + "where device_code in (:codes) and source_sys = :source", nativeQuery = true)
    long countByDeviceCodesAndSourceNative(@Param("codes") Collection<String> codes, @Param("source") String source);

    @Query(value = "select coalesce(sum(total_qty), 0) from wms_device_stat_daily where device_code in (:codes)", nativeQuery = true)
    BigDecimal sumQtyByDeviceCodesNative(@Param("codes") Collection<String> codes);

    @Query(value = "select coalesce(sum(total_qty), 0) from wms_device_stat_daily "
            + "where device_code in (:codes) and source_sys = :source", nativeQuery = true)
    BigDecimal sumQtyByDeviceCodesAndSourceNative(@Param("codes") Collection<String> codes, @Param("source") String source);

    /* ---------- 备件寿命 HOURS / TASKS 口径：设备没接实时视图时的兜底 ---------- */

    @Query(value = "select count(*) from wms_device_stat_daily where device_code = :code and running_hours > 0",
            nativeQuery = true)
    long countRunningHoursByDeviceCodeNative(@Param("code") String code);

    @Query(value = "select coalesce(sum(running_hours), 0) from wms_device_stat_daily where device_code = :code",
            nativeQuery = true)
    BigDecimal sumRunningHoursByDeviceCodeNative(@Param("code") String code);

    @Query(value = "select count(*) from wms_device_stat_daily where device_code = :code and task_count > 0",
            nativeQuery = true)
    long countTaskCountByDeviceCodeNative(@Param("code") String code);

    @Query(value = "select coalesce(sum(task_count), 0) from wms_device_stat_daily where device_code = :code",
            nativeQuery = true)
    long sumTaskCountByDeviceCodeNative(@Param("code") String code);

    @Query(value = "select count(*) from wms_device_stat_daily where device_code in (:codes) and running_hours > 0",
            nativeQuery = true)
    long countRunningHoursByDeviceCodesNative(@Param("codes") Collection<String> codes);

    @Query(value = "select coalesce(sum(running_hours), 0) from wms_device_stat_daily where device_code in (:codes)",
            nativeQuery = true)
    BigDecimal sumRunningHoursByDeviceCodesNative(@Param("codes") Collection<String> codes);

    @Query(value = "select count(*) from wms_device_stat_daily where device_code in (:codes) and task_count > 0",
            nativeQuery = true)
    long countTaskCountByDeviceCodesNative(@Param("codes") Collection<String> codes);

    @Query(value = "select coalesce(sum(task_count), 0) from wms_device_stat_daily where device_code in (:codes)",
            nativeQuery = true)
    long sumTaskCountByDeviceCodesNative(@Param("codes") Collection<String> codes);
}
