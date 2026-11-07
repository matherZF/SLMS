package com.wms.repository;

import com.wms.entity.PutawayOrder;
import com.wms.entity.PutawayStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PutawayOrderRepository extends JpaRepository<PutawayOrder, Long> {
    Page<PutawayOrder> findByStatus(PutawayStatus status, Pageable pageable);
    Optional<PutawayOrder> findTopByOrderNoStartingWithOrderByOrderNoDesc(String prefix);
    boolean existsByProductId(Long productId);

    @Query("select distinct o from PutawayOrder o left join fetch o.product " +
            "where (:status is null or o.status = :status) " +
            "and (:kw is null or :kw = '' or o.product.sku like %:kw% or o.product.name like %:kw%)")
    Page<PutawayOrder> findByStatusAndKeyword(@Param("status") PutawayStatus status,
                                              @Param("kw") String keyword,
                                              Pageable pageable);

    @Query("select distinct o from PutawayOrder o left join fetch o.product where o.status = :status")
    List<PutawayOrder> findByStatusWithProduct(@Param("status") PutawayStatus status);

    @EntityGraph(attributePaths = {"product", "items", "items.location", "batch"})
    @Query("select o from PutawayOrder o where o.id = :id")
    Optional<PutawayOrder> findDetailById(@Param("id") Long id);

    /**
     * 上架确认专用：加行锁读取。
     * <p>
     * 确认上架时要读 putQty、再算 putQty + qty 是否超计划、然后写回。
     * 没有行锁的话两个并发请求会读到同一个 putQty，超收（计划 10 件收进 15 件）
     * 或后写的覆盖先写的（丢失更新）。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"product", "items", "items.location", "batch"})
    @Query("select o from PutawayOrder o where o.id = :id")
    Optional<PutawayOrder> findDetailForUpdate(@Param("id") Long id);
}
