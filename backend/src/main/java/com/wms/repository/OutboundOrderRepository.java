package com.wms.repository;

import com.wms.entity.OutboundOrder;
import com.wms.entity.OutboundStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

public interface OutboundOrderRepository extends JpaRepository<OutboundOrder, Long> {
    long countByStatus(OutboundStatus status);
    Optional<OutboundOrder> findTopByOrderNoStartingWithOrderByOrderNoDesc(String prefix);

    @Query("select o from OutboundOrder o where (:status is null or o.status = :status) and " +
            "(:keyword is null or lower(o.orderNo) like lower(concat('%', :keyword, '%')) " +
            "or lower(coalesce(o.applicant, '')) like lower(concat('%', :keyword, '%')) " +
            "or lower(coalesce(o.department, '')) like lower(concat('%', :keyword, '%')) " +
            "or lower(coalesce(o.recipient, '')) like lower(concat('%', :keyword, '%')) " +
            "or lower(coalesce(o.workOrderNo, '')) like lower(concat('%', :keyword, '%'))) " +
            "order by o.id desc")
    Page<OutboundOrder> search(@Param("status") OutboundStatus status,
                               @Param("keyword") String keyword, Pageable pageable);

    @EntityGraph(attributePaths = {"items"})
    @Query("select o from OutboundOrder o where o.status in :statuses order by o.id desc")
    List<OutboundOrder> findPendingList(@Param("statuses") Collection<OutboundStatus> statuses);

    @EntityGraph(attributePaths = {"equipment", "items", "items.product", "items.location", "items.batch"})
    @Query("select o from OutboundOrder o where o.id = :id")
    Optional<OutboundOrder> findDetailById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"equipment", "items", "items.product", "items.location", "items.batch"})
    @Query("select o from OutboundOrder o where o.id = :id")
    Optional<OutboundOrder> findDetailForUpdate(@Param("id") Long id);
}
