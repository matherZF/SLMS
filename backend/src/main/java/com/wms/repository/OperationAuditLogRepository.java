package com.wms.repository;

import com.wms.entity.OperationAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperationAuditLogRepository extends JpaRepository<OperationAuditLog, Long> {
    List<OperationAuditLog> findByBusinessTypeAndBusinessIdOrderByIdAsc(String businessType, Long businessId);

    @Query("select l from OperationAuditLog l where (:businessType is null or l.businessType = :businessType) and " +
            "(:keyword is null or lower(coalesce(l.businessNo, '')) like lower(concat('%', :keyword, '%')) " +
            "or lower(coalesce(l.operator, '')) like lower(concat('%', :keyword, '%')) " +
            "or lower(coalesce(l.detail, '')) like lower(concat('%', :keyword, '%'))) ")
    Page<OperationAuditLog> search(@Param("businessType") String businessType,
                                   @Param("keyword") String keyword, Pageable pageable);
}
