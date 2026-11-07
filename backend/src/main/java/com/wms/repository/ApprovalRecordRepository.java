package com.wms.repository;

import com.wms.entity.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ApprovalRecordRepository extends JpaRepository<ApprovalRecord, Long> {
    List<ApprovalRecord> findByBusinessTypeAndBusinessIdOrderByRoundNoAscIdAsc(String businessType, Long businessId);
    List<ApprovalRecord> findByStatusOrderBySubmittedAtAsc(ApprovalStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ApprovalRecord a where a.businessType=:type and a.businessId=:id and a.status=:status order by a.id desc")
    List<ApprovalRecord> findCurrentForUpdate(@Param("type") String type, @Param("id") Long id, @Param("status") ApprovalStatus status);
}
