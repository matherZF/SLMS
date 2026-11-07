package com.wms.repository;

import com.wms.entity.NotificationRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationReadRepository extends JpaRepository<NotificationRead, Long> {

    Optional<NotificationRead> findByUsernameAndCategoryAndTargetId(String username, String category, Long targetId);

    List<NotificationRead> findByUsernameAndCategory(String username, String category);

    /** 已读历史：只回看最近一段时间，不至于把很久以前的消息一直挂在弹框里 */
    List<NotificationRead> findByUsernameAndReadAtAfterOrderByReadAtDesc(String username, Instant after);

    /** 顺手清理过期回执，保持表大小可控（在写入时调用即可，不需要定时任务） */
    @Modifying
    @Query("DELETE FROM NotificationRead r WHERE r.readAt < :before")
    int deleteExpired(@Param("before") Instant before);
}
