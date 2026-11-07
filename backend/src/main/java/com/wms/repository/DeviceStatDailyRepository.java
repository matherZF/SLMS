package com.wms.repository;

import com.wms.entity.DeviceStatDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface DeviceStatDailyRepository extends JpaRepository<DeviceStatDaily, Long> {
    List<DeviceStatDaily> findBySourceSysAndStatDateOrderByDeviceTypeAscDeviceCodeAsc(String sourceSys, LocalDate statDate);

    List<DeviceStatDaily> findByDeviceCodeOrderByStatDateDesc(String deviceCode);

    List<DeviceStatDaily> findByStatDateBetween(LocalDate from, LocalDate to);

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
}
