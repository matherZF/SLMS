package com.wms.repository;

import com.wms.entity.SparePartAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SparePartAlertRepository extends JpaRepository<SparePartAlert, Long> {

    /** 同一预警对象只保留一条记录，取最新的那条判断要不要重开 */
    Optional<SparePartAlert> findTopByDedupKeyOrderByIdDesc(String dedupKey);

    List<SparePartAlert> findByStatusOrderBySeverityAscIdDesc(String status);

    long countByStatus(String status);

    /** 判断今天是否已经跑过批：定时任务之外，服务重启后靠它决定要不要补跑一次 */
    long countByUpdatedAtAfter(Instant from);
}
