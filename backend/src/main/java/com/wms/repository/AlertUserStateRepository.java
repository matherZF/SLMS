package com.wms.repository;

import com.wms.entity.AlertUserState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AlertUserStateRepository extends JpaRepository<AlertUserState, Long> {

    Optional<AlertUserState> findByAlertIdAndUserId(Long alertId, Long userId);

    List<AlertUserState> findByUserId(Long userId);

    List<AlertUserState> findByAlertIdInAndUserId(List<Long> alertIds, Long userId);

    /** 统计指定用户可见的 OPEN 预警数（排除该用户已忽略的，读不读都算） */
    @Query(value = """
        SELECT COUNT(*) FROM wms_spare_part_alert a
        WHERE a.status = 'OPEN'
          AND NOT EXISTS (
            SELECT 1 FROM wms_alert_user_state s
            WHERE s.alert_id = a.id
              AND s.user_id = :userId
              AND s.ignored = 1
          )
    """, nativeQuery = true)
    long countVisibleOpenAlerts(@Param("userId") Long userId);

    /** 统计指定用户的未读 OPEN 预警数量（通过子查询关联主表 status） */
    @Query(value = """
        SELECT COUNT(*) FROM wms_spare_part_alert a
        WHERE a.status = 'OPEN'
          AND NOT EXISTS (
            SELECT 1 FROM wms_alert_user_state s
            WHERE s.alert_id = a.id
              AND s.user_id = :userId
              AND s.read_flag = 1
          )
          AND NOT EXISTS (
            SELECT 1 FROM wms_alert_user_state s
            WHERE s.alert_id = a.id
              AND s.user_id = :userId
              AND s.ignored = 1
          )
    """, nativeQuery = true)
    long countUnreadOpenAlerts(@Param("userId") Long userId);

    /** 删除主表已被 ACK 处理后遗留的用户状态（可选，保持表大小可控） */
    @Modifying
    @Query("DELETE FROM AlertUserState s WHERE s.alertId IN :alertIds")
    void deleteByAlertIdIn(@Param("alertIds") List<Long> alertIds);
}
